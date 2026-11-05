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
        IngestionPlan plan = planFactory.activeSeasonPlan();
        log.info("Active-season ingestion plan created: workUnits={}, datasets={}, providers={}",
                plan.units().size(),
                plan.units().stream().map(IngestionWorkUnit::dataset).distinct().toList(),
                plan.units().stream().map(IngestionWorkUnit::provider).distinct().toList());

        if (plan.units().isEmpty()) {
            log.info("No active season has a supported ingestion route; skipping Core sync");
            return;
        }

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
            runStage("catalogue/seasons/" + catalogueProvider,
                    () -> seasonIngestionService.syncSeasonsForProvider(catalogueProvider));
        }

        List<IngestionWorkUnit> footballDataTeams = plan.units(
                Dataset.TEAM, Provider.FOOTBALL_DATA, IngestionWorkUnit.Role.PRIMARY);
        if (!footballDataTeams.isEmpty()) {
            runPlannedStage("teams/FOOTBALL_DATA", Dataset.TEAM, footballDataTeams,
                    () -> teamIngestionService.syncTeamsForProvider(Provider.FOOTBALL_DATA));
        }

        List<IngestionWorkUnit> footballDataFixtures = plan.units(
                Dataset.FIXTURE, Provider.FOOTBALL_DATA, IngestionWorkUnit.Role.PRIMARY);
        if (!footballDataFixtures.isEmpty()) {
            runPlannedStage("fixtures/FOOTBALL_DATA", Dataset.FIXTURE, footballDataFixtures,
                    () -> fixtureIngestionService.syncFixturesForProvider(Provider.FOOTBALL_DATA, null, null));
        }

        List<IngestionWorkUnit> footballDataStandings = plan.units(
                Dataset.STANDINGS, Provider.FOOTBALL_DATA, IngestionWorkUnit.Role.PRIMARY);
        if (!footballDataStandings.isEmpty()) {
            runPlannedStage("standings/FOOTBALL_DATA", Dataset.STANDINGS, footballDataStandings,
                    () -> standingsIngestionService.syncStandingsForProvider(Provider.FOOTBALL_DATA));
        }

        // UK is both primary for 2014-2022 and an explicitly permitted fixture fallback
        // for overlap seasons. Its importer evaluates the same active-season policy and
        // writes its own scoped Core checkpoints.
        boolean ukCoreRequired = plan.units().stream().anyMatch(unit ->
                unit.provider() == Provider.FOOTBALL_DATA_UK
                        && (unit.dataset() == Dataset.TEAM || unit.dataset() == Dataset.FIXTURE));
        if (ukCoreRequired) {
            runStage("football-data-uk/active-seasons", footballDataUkSyncService::syncActiveSeasonsData);
        }

        if (plan.has(Dataset.UPCOMING_ODDS, Provider.API_FOOTBALL, IngestionWorkUnit.Role.PRIMARY)
                || plan.has(Dataset.UPCOMING_ODDS, Provider.FOOTBALL_DATA, IngestionWorkUnit.Role.PRIMARY)) {
            throw new IllegalStateException("Upcoming odds are disabled by DataSourcePolicy");
        }
        if (oddsProviderOverride != null) {
            log.info("Ignoring upcoming odds override {} because DataSourcePolicy disables UPCOMING_ODDS",
                    oddsProviderOverride);
        }
        log.info("Active-season ingestion completed");
    }

    private void runPlannedStage(String name, Dataset dataset,
                                 List<IngestionWorkUnit> units, Runnable operation) {
        units.forEach(unit -> checkpointStore.markStatus(unit.provider(), dataset,
                unit.leagueId(), unit.season(), CheckpointStatus.RUNNING));
        try {
            runStage(name, operation);
            units.forEach(unit -> checkpointStore.markStatus(unit.provider(), dataset,
                    unit.leagueId(), unit.season(), CheckpointStatus.COMPLETED));
        } catch (IngestionPartialException exception) {
            units.forEach(unit -> checkpointStore.markStatus(unit.provider(), dataset,
                    unit.leagueId(), unit.season(), CheckpointStatus.PARTIAL));
            log.warn("Ingestion stage completed partially: {} reason={}", name, exception.getMessage());
        } catch (RuntimeException exception) {
            units.forEach(unit -> checkpointStore.markStatus(unit.provider(), dataset,
                    unit.leagueId(), unit.season(), CheckpointStatus.FAILED));
            throw exception;
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
