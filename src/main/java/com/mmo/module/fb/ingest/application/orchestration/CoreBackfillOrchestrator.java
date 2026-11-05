package com.mmo.module.fb.ingest.application.orchestration;

import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.application.lock.IngestionDistributedLock;
import com.mmo.module.fb.ingest.application.exception.IngestionPartialException;
import com.mmo.module.fb.ingest.application.port.out.FootballDataUkIngestionPort;
import com.mmo.module.fb.ingest.application.port.out.ProviderCheckpointStore;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.enums.CheckpointStatus;
import com.mmo.module.fb.ingest.policy.Dataset;
import com.mmo.module.fb.ingest.provider.client.ProviderTransportException;

import com.mmo.module.fb.ingest.application.port.in.FixtureIngestionService;
import com.mmo.module.fb.ingest.application.port.in.FootballDataBootstrapService;
import com.mmo.module.fb.ingest.application.port.in.LeagueIngestionService;
import com.mmo.module.fb.ingest.service.ProviderOverrides;
import com.mmo.module.fb.ingest.application.port.in.SeasonIngestionService;
import com.mmo.module.fb.ingest.application.port.in.StandingsIngestionService;
import com.mmo.module.fb.ingest.application.port.in.TeamIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;


import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Coordinates the one-time historical Core backfill and recovery workflow.
 *
 * <p>Football-Data free tier owns seasons from 2023 onward. Football-Data.co.uk is imported
 * separately from 2014 onward. Completion is persisted per resource/provider so a provider
 * selected for one resource cannot mark another resource's historical import as complete.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoreBackfillOrchestrator implements FootballDataBootstrapService {

    private static final String INITIAL_CORE_CHECKPOINT = "CORE_INITIAL_BACKFILL";

    private final IngestionDistributedLock distributedLock;
    private final AppProperties appProperties;
    private final ProviderCheckpointStore checkpointStore;
    private final IngestionPlanFactory planFactory;
    private final FootballDataUkIngestionPort footballDataUkSyncService;
    private final LeagueIngestionService leagueIngestionService;
    private final SeasonIngestionService seasonIngestionService;
    private final TeamIngestionService teamIngestionService;
    private final FixtureIngestionService fixtureIngestionService;
    private final StandingsIngestionService standingsIngestionService;

    @Override
    public void syncCoreData() {
        syncCoreData(ProviderOverrides.defaults());
    }

    @Override
    public void syncCoreData(ProviderOverrides overrides) {
        boolean executed = distributedLock.execute(IngestionDistributedLock.GLOBAL_INGEST_LOCK,
                () -> syncCoreDataLocked(overrides));
        if (!executed) {
            log.warn("Bootstrap skipped because another ingestion workflow is running");
        }
    }

    private void syncCoreDataLocked(ProviderOverrides overrides) {
        List<String> failedStages = new ArrayList<>();
        // Catalogue bootstrap precedes plan construction because a cold database may not
        // contain any Season rows yet. The catalogue provider supplies identities only;
        // DataSourcePolicy chooses the owner independently for every season/dataset.
        validateProvider(Provider.FOOTBALL_DATA);
        runStageCollecting("league-catalogue/FOOTBALL_DATA",
                () -> leagueIngestionService.syncLeaguesForProvider(Provider.FOOTBALL_DATA), failedStages);
        runStageCollecting("season-catalogue/FOOTBALL_DATA",
                () -> seasonIngestionService.syncSeasonsForProvider(Provider.FOOTBALL_DATA), failedStages);

        if (overrides != null && (overrides.team() != null || overrides.fixture() != null
                || overrides.standings() != null)) {
            log.info("Ignoring resource-level provider overrides for historical Core; "
                    + "DataSourcePolicy is authoritative per dataset and season");
        }

        boolean ukCoreRequired = isProviderEnabled(Provider.FOOTBALL_DATA_UK);
        if (ukCoreRequired) {
            try {
                footballDataUkSyncService.prepareHistoricalSeasons();
            } catch (RuntimeException exception) {
                failedStages.add("football-data-uk/prepare-seasons: " + exception.getMessage());
                log.error("Unable to prepare Football-Data UK historical seasons", exception);
            }
        }

        IngestionPlan plan = planFactory.historicalPlan();
        List<IngestionWorkUnit> footballDataSeasons = plan.units(
                Dataset.SEASON, Provider.FOOTBALL_DATA, IngestionWorkUnit.Role.PRIMARY);
        if (failedStages.stream().noneMatch(error -> error.startsWith("season-catalogue/"))) {
            footballDataSeasons.forEach(unit -> checkpointStore.markStatus(unit.provider(), Dataset.SEASON,
                    unit.leagueId(), unit.season(), CheckpointStatus.COMPLETED));
        }
        List<IngestionWorkUnit> footballDataTeams = plan.units(
                Dataset.TEAM, Provider.FOOTBALL_DATA, IngestionWorkUnit.Role.PRIMARY);
        List<IngestionWorkUnit> footballDataFixtures = plan.units(
                Dataset.FIXTURE, Provider.FOOTBALL_DATA, IngestionWorkUnit.Role.PRIMARY);
        log.info("Historical Core plan created: units={}, footballDataTeams={}, "
                        + "footballDataFixtures={}, footballDataUkRequired={}",
                plan.units().size(), footballDataTeams.size(), footballDataFixtures.size(), ukCoreRequired);

        if (plan.units().isEmpty() && !ukCoreRequired) {
            if (!failedStages.isEmpty()) {
                throw new IllegalStateException("Historical Core catalogue initialization incomplete: "
                        + failedStages);
            }
            log.info("Historical Core plan contains no supported season work; nothing to backfill");
            return;
        }

        List<IngestionUnitResult> unitResults = new ArrayList<>();
        footballDataTeams.forEach(unit -> runUnit(unit, () ->
                teamIngestionService.syncSeasonForProvider(unit.provider(), unit.seasonId()), failedStages, unitResults));
        footballDataFixtures.forEach(unit -> runUnit(unit, () ->
                fixtureIngestionService.syncSeasonForProvider(unit.provider(), unit.seasonId(), null, null),
                failedStages, unitResults));

        if (ukCoreRequired) {
            // The CSV endpoint is season-scoped. A single request imports one season only;
            // its TEAM/SEASON/FIXTURE checkpoint outcomes are persisted independently.
            List<Long> ukSeasonIds = plan.units().stream()
                    .filter(unit -> unit.provider() == Provider.FOOTBALL_DATA_UK
                            && (unit.dataset() == Dataset.SEASON || unit.dataset() == Dataset.TEAM
                            || unit.dataset() == Dataset.FIXTURE))
                    .map(IngestionWorkUnit::seasonId).distinct().sorted().toList();
            for (Long seasonId : ukSeasonIds) {
                try {
                    runStage("football-data-uk/core/season=" + seasonId,
                            () -> footballDataUkSyncService.syncCoreSeason(seasonId, true));
                } catch (RuntimeException exception) {
                    failedStages.add("football-data-uk/core/season=" + seasonId
                            + ": " + exception.getMessage());
                    log.warn("Football-Data UK season failed; continuing with remaining seasons: {}",
                            seasonId, exception);
                }
            }
            if (failedStages.stream().noneMatch(error -> error.startsWith("football-data-uk/"))) {
                checkpointStore.markCompleted(Provider.FOOTBALL_DATA_UK, INITIAL_CORE_CHECKPOINT);
            }
        }

        IngestionExecutionReport report = new IngestionExecutionReport(unitResults);
        log.info("Historical Core work-unit report: {}", report.summary());
        if (!failedStages.isEmpty()) {
            throw new IllegalStateException("Historical Core backfill incomplete: " + failedStages
                    + "; " + report.summary());
        }
        log.info("Historical Core backfill orchestration completed: {}", report.summary());
    }

    private void runUnit(IngestionWorkUnit unit, Runnable operation, List<String> failedStages,
                         List<IngestionUnitResult> results) {
        String name = unit.provider() + "/" + unit.dataset() + "/league=" + unit.leagueId()
                + "/season=" + unit.season();
        IngestionUnitResult result = new IngestionUnitExecutor(checkpointStore)
                .execute(unit, true, () -> runStage(name, operation));
        results.add(result);
        if (!result.isSuccessful()) {
            failedStages.add(name + " [" + result.status() + "]: " + result.message());
            log.warn("Independent historical work unit finished with {}: {}", result.status(), name);
        }
    }

    /**
     * Explicit multi-provider historical import. Each provider is mapped to the same
     * canonical Core entities before teams and fixtures are imported.
     */
    @Override
    public void syncCoreDataForProviders(List<Provider> providers) {
        boolean executed = distributedLock.execute(IngestionDistributedLock.GLOBAL_INGEST_LOCK,
                () -> syncCoreDataForProvidersLocked(providers));
        if (!executed) {
            log.warn("Multi-provider bootstrap skipped because another ingestion workflow is running");
        }
    }

    private void syncCoreDataForProvidersLocked(List<Provider> providers) {
        if (providers == null || providers.isEmpty()) {
            throw new IllegalArgumentException("At least one ingestion provider is required");
        }
        if (providers.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Provider list must not contain null");
        }

        List<Provider> ordered = new ArrayList<>(new LinkedHashSet<>(providers));
        ordered.sort((left, right) -> {
            if (left == right) return 0;
            if (left == Provider.FOOTBALL_DATA) return -1;
            if (right == Provider.FOOTBALL_DATA) return 1;
            return left.name().compareTo(right.name());
        });
        ordered.forEach(this::validateProvider);

        log.info("Starting multi-provider historical Core import: {}", ordered);
        for (Provider provider : ordered) {
            runStage("multi-provider/leagues/" + provider,
                    () -> leagueIngestionService.syncLeaguesForProvider(provider));
        }
        for (Provider provider : ordered) {
            runStage("multi-provider/seasons/" + provider,
                    () -> seasonIngestionService.syncSeasonsForProvider(provider));
        }
        IngestionPlan plan = planFactory.historicalPlan();
        List<String> failedStages = new ArrayList<>();
        List<IngestionUnitResult> results = new ArrayList<>();
        for (Provider provider : ordered) {
            plan.units(Dataset.TEAM, provider, IngestionWorkUnit.Role.PRIMARY)
                    .forEach(unit -> runUnit(unit, () ->
                            teamIngestionService.syncSeasonForProvider(provider, unit.seasonId()),
                            failedStages, results));
            plan.units(Dataset.FIXTURE, provider, IngestionWorkUnit.Role.PRIMARY)
                    .forEach(unit -> runUnit(unit, () ->
                            fixtureIngestionService.syncSeasonForProvider(provider, unit.seasonId(), null, null),
                            failedStages, results));
            plan.units(Dataset.STANDINGS, provider, IngestionWorkUnit.Role.PRIMARY)
                    .forEach(unit -> runUnit(unit, () ->
                            standingsIngestionService.syncSeasonForProvider(provider, unit.seasonId()),
                            failedStages, results));
        }
        IngestionExecutionReport report = new IngestionExecutionReport(results);
        log.info("Multi-provider historical Core execution report: providers={}, {}", ordered, report.summary());
        if (!failedStages.isEmpty()) {
            throw new IllegalStateException("Multi-provider historical Core import incomplete: "
                    + failedStages + "; " + report.summary());
        }
        log.info("Multi-provider historical Core import completed: {}", ordered);
    }

    private boolean isProviderEnabled(Provider provider) {
        AppProperties.ProviderConfig config = appProperties.getIngestion().getProviderConfig().get(provider);
        return config != null && config.isEnabled();
    }

    private void runStageCollecting(String name, Runnable operation, List<String> failedStages) {
        try {
            runStage(name, operation);
        } catch (RuntimeException exception) {
            failedStages.add(name + ": " + exception.getMessage());
            log.warn("Catalogue stage failed; continuing independent backfill units: {}", name, exception);
        }
    }

    private void runStage(String name, Runnable operation) {
        long startedAt = System.nanoTime();
        log.info("Core ingestion stage STARTED: {}", name);
        try {
            operation.run();
            log.info("Core ingestion stage COMPLETED: {} durationMs={}",
                    name, (System.nanoTime() - startedAt) / 1_000_000);
        } catch (ProviderTransportException exception) {
            log.error("Core ingestion stage FAILED: {} durationMs={}",
                    name, (System.nanoTime() - startedAt) / 1_000_000, exception);
            throw exception;
        } catch (RuntimeException exception) {
            log.error("Core ingestion stage FAILED: {} durationMs={} errorType={} message={}",
                    name, (System.nanoTime() - startedAt) / 1_000_000,
                    exception.getClass().getSimpleName(), exception.getMessage(), exception);
            throw exception;
        }
    }

    private void validateProvider(Provider provider) {
        if (provider != Provider.FOOTBALL_DATA) {
            throw new IllegalStateException("Phase 1 historical API bootstrap supports FOOTBALL_DATA only; "
                    + provider + " is temporarily disabled. Football-Data UK uses its dedicated CSV importer.");
        }
        AppProperties.ProviderConfig config = appProperties.getIngestion().getProviderConfig().get(provider);
        if (config == null || !config.isEnabled()) {
            throw new IllegalStateException(provider + " is disabled or not configured for bootstrap");
        }
        if (provider == Provider.API_FOOTBALL || provider == Provider.FOOTBALL_DATA) {
            String variableName = provider == Provider.API_FOOTBALL
                    ? "API_FOOTBALL_API_KEY" : "FOOTBALL_DATA_API_KEY";
            String token = config == null ? null : config.getApiKey();
            if (!StringUtils.hasText(token) || "mock".equalsIgnoreCase(token.trim())) {
                throw new IllegalStateException("Provider API token is not configured. Set " + variableName + ".");
            }
        }
    }
}
