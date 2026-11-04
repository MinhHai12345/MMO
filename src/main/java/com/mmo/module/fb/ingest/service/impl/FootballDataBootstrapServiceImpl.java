package com.mmo.module.fb.ingest.service.impl;

import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.entity.ProviderSyncCheckpoint;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.client.ProviderTransportException;
import com.mmo.module.fb.ingest.repository.ProviderSyncCheckpointRepository;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Coordinates the one-time historical Core backfill and subsequent current-season sync.
 *
 * <p>Football-Data free tier owns seasons from 2023 onward. Football-Data.co.uk is imported
 * separately from 2014 onward. Completion is persisted per provider so restarts and retries
 * cannot accidentally turn every scheduled bootstrap into another historical import.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FootballDataBootstrapServiceImpl implements FootballDataBootstrapService {

    private static final String INITIAL_CORE_CHECKPOINT = "CORE_INITIAL_BACKFILL";
    private static final String TEAM_BACKFILL_CHECKPOINT = "CORE_BACKFILL_TEAMS";
    private static final String FIXTURE_BACKFILL_CHECKPOINT = "CORE_BACKFILL_FIXTURES";

    private final AppProperties appProperties;
    private final IngestionServiceSupport ingestionSupport;
    private final ProviderSyncCheckpointRepository checkpointRepository;
    private final FootballDataUkSyncService footballDataUkSyncService;
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
        ProviderOverrides selected = overrides == null ? ProviderOverrides.defaults() : overrides;
        AppProperties.Providers defaults = appProperties.getIngestion().getProviders();

        Provider leagueProvider = selected.league() == null ? defaults.getLeague() : selected.league();
        Provider seasonProvider = selected.season() == null ? defaults.getSeason() : selected.season();
        Provider teamProvider = selected.team() == null ? defaults.getTeam() : selected.team();
        Provider fixtureProvider = selected.fixture() == null ? defaults.getFixture() : selected.fixture();
        Provider standingsProvider = selected.standings() == null ? defaults.getStandings() : selected.standings();

        Set<Provider> coreProviders = new LinkedHashSet<>(List.of(
                leagueProvider, seasonProvider, teamProvider, fixtureProvider));
        coreProviders.forEach(provider -> validateProvider(provider));
        if (standingsProvider != Provider.UNDER_STATS) validateProvider(standingsProvider);

        boolean teamsBackfillRequired = !isResourceBackfillCompleted(teamProvider, TEAM_BACKFILL_CHECKPOINT);
        boolean fixturesBackfillRequired = !isResourceBackfillCompleted(fixtureProvider, FIXTURE_BACKFILL_CHECKPOINT);
        boolean csvBackfillCompleted = isInitialBackfillCompleted(Provider.FOOTBALL_DATA_UK);

        // Bootstrap is a recovery entry point, not a second incremental-sync scheduler.
        // Once every historical resource has its own durable checkpoint, recurring updates
        // belong exclusively to FootballDataSyncJob.
        if (!teamsBackfillRequired && !fixturesBackfillRequired && csvBackfillCompleted) {
            log.info("Historical Core backfill already completed. providers: teams={}, fixtures={}, csv={}. "
                    + "Skipping bootstrap; recurring updates belong to FootballDataSyncJob.",
                    teamProvider, fixtureProvider, Provider.FOOTBALL_DATA_UK);
            return;
        }

        log.info("Core bootstrap mode: teams={}, fixtures={}, providers: league={}, season={}, team={}, "
                        + "fixture={}, standings={}",
                teamsBackfillRequired ? "HISTORICAL_BACKFILL" : "ACTIVE_SEASONS_ONLY",
                fixturesBackfillRequired ? "HISTORICAL_BACKFILL" : "ACTIVE_SEASONS_ONLY",
                leagueProvider, seasonProvider, teamProvider, fixtureProvider, standingsProvider);

        Set<Provider> catalogueProviders = new LinkedHashSet<>(coreProviders);
        if (standingsProvider != Provider.UNDER_STATS) catalogueProviders.add(standingsProvider);
        for (Provider provider : catalogueProviders) {
            runStage("league-catalogue/" + provider,
                    () -> leagueIngestionService.syncLeaguesForProvider(provider));
        }

        runStage("season-catalogue/" + seasonProvider,
                () -> seasonIngestionService.syncSeasonsForProvider(seasonProvider));

        if (teamsBackfillRequired) {
            runStage("teams/historical/" + teamProvider,
                    () -> teamIngestionService.syncAllSeasonsForProvider(teamProvider));
            completeResourceBackfill(teamProvider, TEAM_BACKFILL_CHECKPOINT);
        } else {
            runStage("teams/active-seasons/" + teamProvider,
                    () -> teamIngestionService.syncTeamsForProvider(teamProvider));
        }

        if (fixturesBackfillRequired) {
            runStage("fixtures/historical/" + fixtureProvider,
                    () -> fixtureIngestionService.syncAllSeasonsForProvider(fixtureProvider, null, null));
            completeResourceBackfill(fixtureProvider, FIXTURE_BACKFILL_CHECKPOINT);
        } else {
            runStage("fixtures/active-seasons/" + fixtureProvider,
                    () -> fixtureIngestionService.syncFixturesForProvider(fixtureProvider, null, null));
        }

        if (standingsProvider == Provider.UNDER_STATS) {
            log.info("Skipping standings: UNDER_STATS does not expose canonical standings");
        } else {
            runStage("standings/current/" + standingsProvider,
                    () -> standingsIngestionService.syncStandingsForProvider(standingsProvider));
        }

        if (csvBackfillCompleted) {
            runStage("football-data-uk/current-season", footballDataUkSyncService::syncCurrentSeasonData);
        } else if (footballDataUkSyncService.syncInitialData()) {
            completeInitialBackfill(Provider.FOOTBALL_DATA_UK);
        }

        log.info("Core bootstrap completed. teamsMode={}, fixturesMode={}, csvMode={}",
                teamsBackfillRequired ? "HISTORICAL" : "ACTIVE_SEASONS",
                fixturesBackfillRequired ? "HISTORICAL" : "ACTIVE_SEASONS",
                csvBackfillCompleted ? "CURRENT_SEASON" : "INITIAL_HISTORICAL");
    }
    /**
     * Explicit multi-provider historical import. Each provider is mapped to the same
     * canonical Core entities before teams and fixtures are imported.
     */
    @Override
    public void syncCoreDataForProviders(List<Provider> providers) {
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

    private boolean isResourceBackfillCompleted(Provider provider, String checkpointKey) {
        return checkpointRepository.existsByProviderAndCheckpointKey(provider, checkpointKey);
    }

    private void completeResourceBackfill(Provider provider, String checkpointKey) {
        ProviderSyncCheckpoint checkpoint = checkpointRepository
                .findByProviderAndCheckpointKey(provider, checkpointKey)
                .orElseGet(ProviderSyncCheckpoint::new);
        checkpoint.setProvider(provider);
        checkpoint.setCheckpointKey(checkpointKey);
        checkpoint.setCompletedAt(Instant.now());
        checkpointRepository.save(checkpoint);
        log.info("Initial Core resource backfill checkpoint completed: provider={}, resource={}",
                provider, checkpointKey);
    }

    private boolean isInitialBackfillCompleted(Provider provider) {
        return checkpointRepository.existsByProviderAndCheckpointKey(provider, INITIAL_CORE_CHECKPOINT);
    }

    private void completeInitialBackfill(Provider provider) {
        ProviderSyncCheckpoint checkpoint = checkpointRepository
                .findByProviderAndCheckpointKey(provider, INITIAL_CORE_CHECKPOINT)
                .orElseGet(ProviderSyncCheckpoint::new);
        checkpoint.setProvider(provider);
        checkpoint.setCheckpointKey(INITIAL_CORE_CHECKPOINT);
        checkpoint.setCompletedAt(Instant.now());
        checkpointRepository.save(checkpoint);
        log.info("Initial Core backfill checkpoint completed: provider={}", provider);
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
        ingestionSupport.requireProviderEnabled(provider, "bootstrap");
        if (provider == Provider.API_FOOTBALL || provider == Provider.FOOTBALL_DATA) {
            AppProperties.ProviderConfig config = appProperties.getIngestion().getProviderConfig().get(provider);
            String variableName = provider == Provider.API_FOOTBALL
                    ? "API_FOOTBALL_API_KEY" : "FOOTBALL_DATA_API_KEY";
            String token = config == null ? null : config.getApiKey();
            if (!StringUtils.hasText(token) || "mock".equalsIgnoreCase(token.trim())) {
                throw new IllegalStateException("Provider API token is not configured. Set " + variableName + ".");
            }
        }
    }
}
