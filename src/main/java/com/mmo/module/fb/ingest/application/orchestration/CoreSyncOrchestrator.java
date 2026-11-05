package com.mmo.module.fb.ingest.application.orchestration;

import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.application.lock.IngestionDistributedLock;
import com.mmo.module.fb.ingest.application.port.out.FootballDataUkIngestionPort;
import com.mmo.module.fb.ingest.application.port.out.ProviderCheckpointStore;
import com.mmo.module.fb.ingest.application.exception.IngestionPartialException;
import com.mmo.module.fb.ingest.enums.CheckpointStatus;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.Dataset;
import com.mmo.module.fb.ingest.service.FixtureIngestionService;
import com.mmo.module.fb.ingest.service.FootballDataSyncService;
import com.mmo.module.fb.ingest.service.LeagueIngestionService;
import com.mmo.module.fb.ingest.service.ProviderOverrides;
import com.mmo.module.fb.ingest.service.StandingsIngestionService;
import com.mmo.module.fb.ingest.service.SeasonIngestionService;
import com.mmo.module.fb.ingest.service.TeamIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Executes the active-season plan. Provider ownership is decided per dataset/season
 * by DataSourcePolicy; provider defaults are used only for catalogue initialization.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoreSyncOrchestrator implements FootballDataSyncService {

    private final IngestionDistributedLock distributedLock;
    private final AppProperties properties;
    private final IngestionPlanFactory planFactory;
    private final ProviderCheckpointStore checkpointStore;
    private final LeagueIngestionService leagueIngestionService;
    private final SeasonIngestionService seasonIngestionService;
    private final TeamIngestionService teamIngestionService;
    private final FixtureIngestionService fixtureIngestionService;
    private final StandingsIngestionService standingsIngestionService;
    private final FootballDataUkIngestionPort footballDataUkSyncService;

    @Override
    public void sync(ProviderOverrides overrides, Provider oddsProviderOverride) {
        boolean executed = distributedLock.execute(IngestionDistributedLock.GLOBAL_INGEST_LOCK,
                () -> syncLocked(overrides, oddsProviderOverride));
        if (!executed) {
            log.warn("Core sync skipped because another ingestion workflow is running");
        }
    }

    private void syncLocked(ProviderOverrides overrides, Provider oddsProviderOverride) {
        if (overrides != null && (overrides.league() != null || overrides.season() != null
                || overrides.team() != null || overrides.fixture() != null || overrides.standings() != null)) {
            log.info("Ignoring resource provider overrides for incremental sync; "
                    + "DataSourcePolicy is authoritative per dataset and season");
        }
        IngestionPlan plan = planFactory.activeSeasonPlan();
        log.info("Active-season ingestion plan created: workUnits={}, datasets={}, providers={}",
                plan.units().size(),
                plan.units().stream().map(IngestionWorkUnit::dataset).distinct().toList(),
                plan.units().stream().map(IngestionWorkUnit::provider).distinct().toList());

        if (plan.units().isEmpty()) {
            log.info("No active season has a supported ingestion route; skipping Core sync");
            return;
        }

        List<String> failedStages = new java.util.ArrayList<>();
        // League/season catalogues are provider-specific identity namespaces. They are
        // initialized before the season-scoped resources; season routing itself remains
        // controlled by the plan rather than the global resource provider properties.
        boolean footballDataCatalogueRequired = plan.units().stream().anyMatch(unit ->
                unit.provider() == Provider.FOOTBALL_DATA
                        && unit.role() == IngestionWorkUnit.Role.PRIMARY);
        if (footballDataCatalogueRequired) {
            Provider catalogueProvider = properties.getIngestion().getProviders().getLeague();
            if (catalogueProvider != Provider.FOOTBALL_DATA) {
                throw new IllegalStateException("Football-Data is the catalogue owner in DataSourcePolicy, got "
                        + catalogueProvider);
            }
            runStage("catalogue/leagues/" + catalogueProvider,
                    () -> leagueIngestionService.syncLeaguesForProvider(catalogueProvider));
            List<IngestionWorkUnit> footballDataSeasons = plan.units(
                    Dataset.SEASON, Provider.FOOTBALL_DATA, IngestionWorkUnit.Role.PRIMARY);
            if (footballDataSeasons.isEmpty()) {
                runStage("catalogue/seasons/" + catalogueProvider,
                        () -> seasonIngestionService.syncSeasonsForProvider(catalogueProvider));
            } else {
                runPlannedStage("catalogue/seasons/" + catalogueProvider, Dataset.SEASON,
                        footballDataSeasons,
                        () -> seasonIngestionService.syncSeasonsForProvider(catalogueProvider), failedStages);
            }
        }

        plan.units(Dataset.TEAM, Provider.FOOTBALL_DATA, IngestionWorkUnit.Role.PRIMARY)
                .forEach(unit -> runUnit(unit, () ->
                        teamIngestionService.syncSeasonForProvider(unit.provider(), unit.seasonId()), failedStages));
        plan.units(Dataset.FIXTURE, Provider.FOOTBALL_DATA, IngestionWorkUnit.Role.PRIMARY)
                .forEach(unit -> runUnit(unit, () ->
                        fixtureIngestionService.syncSeasonForProvider(unit.provider(), unit.seasonId(), null, null),
                        failedStages));
        plan.units(Dataset.STANDINGS, Provider.FOOTBALL_DATA, IngestionWorkUnit.Role.PRIMARY)
                .forEach(unit -> runUnit(unit, () ->
                        standingsIngestionService.syncSeasonForProvider(unit.provider(), unit.seasonId()),
                        failedStages));

        // UK is both primary for 2014-2022 and an explicitly permitted fixture fallback
        // for overlap seasons. Its importer evaluates the same active-season policy and
        // writes its own scoped Core checkpoints.
        boolean ukCoreRequired = plan.units().stream().anyMatch(unit ->
                unit.provider() == Provider.FOOTBALL_DATA_UK
                        && (unit.dataset() == Dataset.TEAM || unit.dataset() == Dataset.FIXTURE));
        if (ukCoreRequired) {
            try {
                runStage("football-data-uk/active-seasons", footballDataUkSyncService::syncActiveSeasonsData);
            } catch (RuntimeException exception) {
                failedStages.add("football-data-uk/active-seasons: " + exception.getMessage());
            }
        }

        if (!plan.units(Dataset.UPCOMING_ODDS, IngestionWorkUnit.Role.PRIMARY).isEmpty()) {
            throw new IllegalStateException("Upcoming odds are disabled by DataSourcePolicy");
        }
        if (oddsProviderOverride != null) {
            log.info("Ignoring upcoming odds override {} because DataSourcePolicy disables UPCOMING_ODDS",
                    oddsProviderOverride);
        }
        if (!failedStages.isEmpty()) {
            throw new IllegalStateException("Active-season ingestion incomplete: " + failedStages);
        }
        log.info("Active-season ingestion completed");
    }

    private void runUnit(IngestionWorkUnit unit, Runnable operation, List<String> failedStages) {
        String name = unit.provider() + "/" + unit.dataset() + "/league=" + unit.leagueId()
                + "/season=" + unit.season();
        if (checkpointStore.isSatisfied(unit.provider(), unit.dataset(), unit.leagueId(), unit.season())) {
            log.debug("Skipping satisfied ingestion work unit {}", name);
            return;
        }
        checkpointStore.markStatus(unit.provider(), unit.dataset(), unit.leagueId(),
                unit.season(), CheckpointStatus.RUNNING);
        try {
            runStage(name, operation);
            checkpointStore.markStatus(unit.provider(), unit.dataset(), unit.leagueId(),
                    unit.season(), CheckpointStatus.COMPLETED);
        } catch (IngestionPartialException exception) {
            checkpointStore.markStatus(unit.provider(), unit.dataset(), unit.leagueId(),
                    unit.season(), CheckpointStatus.PARTIAL);
            failedStages.add(name + ": " + exception.getMessage());
        } catch (RuntimeException exception) {
            checkpointStore.markStatus(unit.provider(), unit.dataset(), unit.leagueId(),
                    unit.season(), CheckpointStatus.FAILED);
            failedStages.add(name + ": " + exception.getMessage());
            log.warn("Independent ingestion work unit failed; continuing: {}", name, exception);
        }
    }

    private void runStage(String name, Runnable operation) {
        long started = System.nanoTime();
        log.info("Ingestion stage STARTED: {}", name);
        try {
            operation.run();
            log.info("Ingestion stage COMPLETED: {} durationMs={}",
                    name, (System.nanoTime() - started) / 1_000_000);
        } catch (RuntimeException exception) {
            log.error("Ingestion stage FAILED: {} durationMs={} errorType={} message={}",
                    name, (System.nanoTime() - started) / 1_000_000,
                    exception.getClass().getSimpleName(), exception.getMessage(), exception);
            throw exception;
        }
    }
}
