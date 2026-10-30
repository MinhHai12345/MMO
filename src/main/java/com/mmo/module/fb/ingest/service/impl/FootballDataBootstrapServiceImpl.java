package com.mmo.module.fb.ingest.service.impl;

import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.service.FixtureIngestionService;
import com.mmo.module.fb.ingest.service.FootballDataBootstrapService;
import com.mmo.module.fb.ingest.service.LeagueIngestionService;
import com.mmo.module.fb.ingest.service.ProviderOverrides;
import com.mmo.module.fb.ingest.service.StandingsIngestionService;
import com.mmo.module.fb.ingest.service.TeamIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Runs the core-data pipeline in dependency order using the provider configured for each resource.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FootballDataBootstrapServiceImpl implements FootballDataBootstrapService {
    private final AppProperties appProperties;
    private final IngestionServiceSupport ingestionSupport;
    private final LeagueIngestionService leagueIngestionService;
    private final TeamIngestionService teamIngestionService;
    private final FixtureIngestionService fixtureIngestionService;
    private final StandingsIngestionService standingsIngestionService;

    @Override
    public void syncCoreData() {
        syncCoreData(ProviderOverrides.defaults());
    }

    @Override
    public void syncCoreData(ProviderOverrides overrides) {
        overrides = overrides == null ? ProviderOverrides.defaults() : overrides;
        AppProperties.Providers defaults = appProperties.getIngestion().getProviders();
        Provider leagueProvider = overrides.league() != null ? overrides.league() : defaults.getLeague();
        Provider teamProvider = overrides.team() != null ? overrides.team() : defaults.getTeam();
        Provider fixtureProvider = overrides.fixture() != null ? overrides.fixture() : defaults.getFixture();
        Provider standingsProvider = overrides.standings() != null ? overrides.standings() : defaults.getStandings();

        validateProvider(leagueProvider);
        validateProvider(teamProvider);
        validateProvider(fixtureProvider);
        validateProvider(standingsProvider);

        log.info("Starting core-data bootstrap: league={}, team={}, fixture={}, standings={}",
                leagueProvider, teamProvider, fixtureProvider, standingsProvider);

        runStage("leagues", () -> leagueIngestionService.syncLeaguesForProvider(leagueProvider));

        // UnderStat catalogue sync (invoked by the league stage) already imports teams and
        // player-season data. Its current integration does not provide canonical fixtures
        // or standings, so those unsupported stages are intentionally skipped.
        if (teamProvider == Provider.UNDER_STATS && leagueProvider == Provider.UNDER_STATS) {
            log.info("Skipping generic team stage: UnderStat catalogue stage already persisted teams");
        } else if (teamProvider == Provider.UNDER_STATS) {
            throw new IllegalArgumentException(
                    "UNDER_STATS team ingestion requires provider.league=UNDER_STATS in the same bootstrap run");
        } else {
            runStage("teams", () -> teamIngestionService.syncTeamsForProvider(teamProvider));
        }

        if (fixtureProvider == Provider.UNDER_STATS) {
            log.info("Skipping fixture stage: UNDER_STATS does not provide the canonical fixture resource");
        } else {
            // Catalogue initialization stores all returned seasons; downstream fetching only processes active leagues.
            runStage("fixtures", () -> fixtureIngestionService.syncFixturesForProvider(fixtureProvider, null, null));
        }

        if (standingsProvider == Provider.UNDER_STATS) {
            log.info("Skipping standings stage: UNDER_STATS does not provide the canonical standings resource");
        } else {
            runStage("standings", () -> standingsIngestionService.syncStandingsForProvider(standingsProvider));
        }

        log.info("Core-data bootstrap completed. Selected providers: league={}, team={}, fixture={}, standings={}",
                leagueProvider, teamProvider, fixtureProvider, standingsProvider);
    }

    /**
     * Imports the same canonical resource set from several providers. The canonical provider
     * runs first so secondary providers can attach IDs to existing core records instead of
     * creating duplicates. Standings are stored only from FOOTBALL_DATA because the current
     * standing schema has no provider dimension; odds are intentionally a separate workflow.
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

        for (Provider provider : ordered) {
            validateProvider(provider);
        }

        log.info("Starting multi-provider core ingestion. Canonical provider first; providers={}", ordered);
        runStage("multi-provider/leagues",
                () -> ordered.forEach(league -> leagueIngestionService.syncLeaguesForProvider(league)));
        runStage("multi-provider/teams",
                () -> ordered.forEach(provider -> teamIngestionService.syncTeamsForProvider(provider)));
        runStage("multi-provider/fixtures",
                () -> ordered.forEach(provider -> fixtureIngestionService.syncFixturesForProvider(provider, null, null)));

        if (ordered.contains(Provider.FOOTBALL_DATA)) {
            runStage("standings/FOOTBALL_DATA",
                    () -> standingsIngestionService.syncStandingsForProvider(Provider.FOOTBALL_DATA));
        } else {
            log.warn("Skipping standings in multi-provider ingestion: current standings table is not provider-scoped. "
                    + "Include FOOTBALL_DATA to refresh canonical standings.");
        }
        log.info("Multi-provider core ingestion finished. providers={}", ordered);
    }

    private void runStage(String resource, Runnable operation) {
        long startedAt = System.nanoTime();
        log.info("Ingestion stage {} status=STARTED", resource);
        try {
            operation.run();
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info("Ingestion stage {} status=COMPLETED durationMs={}", resource, durationMs);
        } catch (RuntimeException ex) {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.error("Ingestion stage {} status=FAILED durationMs={} errorType={} message={}",
                    resource, durationMs, ex.getClass().getSimpleName(), ex.getMessage(), ex);
            throw ex;
        }
    }

    private void validateProvider(Provider provider) {
        ingestionSupport.requireProviderEnabled(provider, "bootstrap");

        if (provider == Provider.API_FOOTBALL) {
            AppProperties.ProviderConfig config = appProperties.getIngestion().getProviderConfig()
                    .get(Provider.API_FOOTBALL);
            requireToken(config == null ? null : config.getApiKey(), "API_FOOTBALL_API_KEY");
        } else if (provider == Provider.FOOTBALL_DATA) {
            AppProperties.ProviderConfig config = appProperties.getIngestion().getProviderConfig()
                    .get(Provider.FOOTBALL_DATA);
            requireToken(config == null ? null : config.getApiKey(), "FOOTBALL_DATA_API_KEY");
        }
    }

    private void requireToken(String token, String variableName) {
        if (!StringUtils.hasText(token) || "mock".equalsIgnoreCase(token.trim())) {
            throw new IllegalStateException("Provider API token is not configured. Set " + variableName + ".");
        }
    }

}
