package com.mmo.module.fb.ingest.application.orchestration;

import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.application.lock.IngestionDistributedLock;
import com.mmo.module.fb.ingest.application.port.out.FootballDataUkIngestionPort;
import com.mmo.module.fb.ingest.application.port.out.ProviderCheckpointStore;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.client.ProviderTransportException;

import com.mmo.module.fb.ingest.service.FixtureIngestionService;
import com.mmo.module.fb.ingest.service.FootballDataBootstrapService;
import com.mmo.module.fb.ingest.service.LeagueIngestionService;
import com.mmo.module.fb.ingest.service.ProviderOverrides;
import com.mmo.module.fb.ingest.service.SeasonIngestionService;
import com.mmo.module.fb.ingest.service.StandingsIngestionService;
import com.mmo.module.fb.ingest.service.TeamIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;


import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
    private static final String TEAM_BACKFILL_CHECKPOINT = "CORE_BACKFILL_TEAMS";
    private static final String FIXTURE_BACKFILL_CHECKPOINT = "CORE_BACKFILL_FIXTURES";

    private final IngestionDistributedLock distributedLock;
    private final AppProperties appProperties;
    private final ProviderCheckpointStore checkpointStore;
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
        ProviderOverrides selected = overrides == null ? ProviderOverrides.defaults() : overrides;
        AppProperties.Providers defaults = appProperties.getIngestion().getProviders();

        Provider leagueProvider = selected.league() == null ? defaults.getLeague() : selected.league();
        Provider seasonProvider = selected.season() == null ? defaults.getSeason() : selected.season();
        Provider teamProvider = selected.team() == null ? defaults.getTeam() : selected.team();
        Provider fixtureProvider = selected.fixture() == null ? defaults.getFixture() : selected.fixture();

        Set<Provider> coreProviders = new LinkedHashSet<>(List.of(
                leagueProvider, seasonProvider, teamProvider, fixtureProvider));

        // A legacy provider-level checkpoint is safe to reuse only when the old and new
        // configurations have one unambiguous Core provider for every resource.
        boolean legacyCheckpointCompatible = coreProviders.size() == 1;
        boolean teamsBackfillRequired = !isResourceBackfillCompleted(
                teamProvider, TEAM_BACKFILL_CHECKPOINT, legacyCheckpointCompatible);
        boolean fixturesBackfillRequired = !isResourceBackfillCompleted(
                fixtureProvider, FIXTURE_BACKFILL_CHECKPOINT, legacyCheckpointCompatible);
        boolean csvProviderEnabled = isProviderEnabled(Provider.FOOTBALL_DATA_UK);
        boolean csvBackfillCompleted = !csvProviderEnabled
                || isInitialBackfillCompleted(Provider.FOOTBALL_DATA_UK);

        // Bootstrap is a historical initialization/recovery path only. Current-season
        // refreshes, standings and odds belong exclusively to FootballDataSyncJob.
        if (!teamsBackfillRequired && !fixturesBackfillRequired && csvBackfillCompleted) {
            log.info("Historical Core backfill already completed. providers: teams={}, fixtures={}, csv={}. "
                    + "Skipping bootstrap; recurring updates belong to FootballDataSyncJob.",
                    teamProvider, fixtureProvider, Provider.FOOTBALL_DATA_UK);
            return;
        }

        log.info("Historical bootstrap mode: teams={}, fixtures={}, csv={}",
                teamsBackfillRequired ? "BACKFILL" : "COMPLETED",
                fixturesBackfillRequired ? "BACKFILL" : "COMPLETED",
                !csvProviderEnabled ? "DISABLED" : csvBackfillCompleted ? "COMPLETED" : "BACKFILL");

        Set<Provider> catalogueProviders = new LinkedHashSet<>();
        if (teamsBackfillRequired || fixturesBackfillRequired) {
            catalogueProviders.add(leagueProvider);
            catalogueProviders.add(seasonProvider);
            if (teamsBackfillRequired) catalogueProviders.add(teamProvider);
            if (fixturesBackfillRequired) catalogueProviders.add(fixtureProvider);
        }
        for (Provider provider : catalogueProviders) {
            validateProvider(provider);
            runStage("league-catalogue/" + provider,
                    () -> leagueIngestionService.syncLeaguesForProvider(provider));
        }

        // Each resource provider owns its own season external-ID namespace. Populate
        // season mappings for every provider that participates in this historical run.
        for (Provider provider : catalogueProviders) {
            runStage("season-catalogue/" + provider,
                    () -> seasonIngestionService.syncSeasonsForProvider(provider));
        }

        if (teamsBackfillRequired) {
            validateProvider(teamProvider);
            runStage("teams/historical/" + teamProvider,
                    () -> teamIngestionService.syncAllSeasonsForProvider(teamProvider));
            checkpointStore.markCompleted(teamProvider, TEAM_BACKFILL_CHECKPOINT);
        }

        if (fixturesBackfillRequired) {
            validateProvider(fixtureProvider);
            runStage("fixtures/historical/" + fixtureProvider,
                    () -> fixtureIngestionService.syncAllSeasonsForProvider(fixtureProvider, null, null));
            checkpointStore.markCompleted(fixtureProvider, FIXTURE_BACKFILL_CHECKPOINT);
        }

        if (!csvProviderEnabled) {
            log.info("Skipping Football-Data UK bootstrap because the provider is disabled");
        } else if (!csvBackfillCompleted && footballDataUkSyncService.syncInitialData()) {
            checkpointStore.markCompleted(Provider.FOOTBALL_DATA_UK, INITIAL_CORE_CHECKPOINT);
        } else if (csvBackfillCompleted) {
            log.info("Football-Data UK historical Core backfill already complete; "
                    + "current-season refresh belongs to FootballDataSyncJob");
        }

        log.info("Historical Core bootstrap completed. teams={}, fixtures={}, csv={}",
                teamsBackfillRequired ? "BACKFILLED" : "ALREADY_COMPLETE",
                fixturesBackfillRequired ? "BACKFILLED" : "ALREADY_COMPLETE",
                !csvProviderEnabled ? "DISABLED" : csvBackfillCompleted ? "ALREADY_COMPLETE" : "ATTEMPTED");
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
        for (Provider provider : ordered) {
            runStage("multi-provider/teams/" + provider,
                    () -> teamIngestionService.syncAllSeasonsForProvider(provider));
        }
        for (Provider provider : ordered) {
            runStage("multi-provider/fixtures/" + provider,
                    () -> fixtureIngestionService.syncAllSeasonsForProvider(provider, null, null));
        }

        if (ordered.contains(Provider.FOOTBALL_DATA)) {
            runStage("multi-provider/standings/FOOTBALL_DATA",
                    () -> standingsIngestionService.syncStandingsForProvider(Provider.FOOTBALL_DATA));
        }
        log.info("Multi-provider historical Core import completed: {}", ordered);
    }

    private boolean isProviderEnabled(Provider provider) {
        AppProperties.ProviderConfig config = appProperties.getIngestion().getProviderConfig().get(provider);
        return config != null && config.isEnabled();
    }

    private boolean isInitialBackfillCompleted(Provider provider) {
        return checkpointStore.isCompleted(provider, INITIAL_CORE_CHECKPOINT);
    }

    private boolean isResourceBackfillCompleted(
            Provider provider, String checkpointKey, boolean legacyCheckpointCompatible) {
        boolean resourceCheckpoint = checkpointStore.isCompleted(provider, checkpointKey);
        if (resourceCheckpoint || !legacyCheckpointCompatible) return resourceCheckpoint;

        // The legacy provider-level checkpoint is safe only for the old default topology,
        // where one provider supplied every Core resource and both historical stages ran
        // before the checkpoint was written.
        return checkpointStore.isCompleted(provider, INITIAL_CORE_CHECKPOINT);
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
