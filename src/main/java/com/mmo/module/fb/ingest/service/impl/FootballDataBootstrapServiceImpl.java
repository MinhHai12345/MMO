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

/**
 * Runs the core-data pipeline in dependency order using the provider configured for each resource.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FootballDataBootstrapServiceImpl implements FootballDataBootstrapService {
    private final AppProperties appProperties;
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

        validateProvider(leagueProvider, true);
        validateProvider(teamProvider, false);
        validateProvider(fixtureProvider, false);
        validateProvider(standingsProvider, false);

        AppProperties.FootballData footballData = appProperties.getFootballData();
        if (fixtureProvider == Provider.FOOTBALL_DATA && footballData != null
                && footballData.getSeasons() != null && !footballData.getSeasons().isEmpty()) {
            log.info("Historical fixture backfill enabled for football-data.org seasons={}. "
                            + "Requests are paced by FootballDataClient; seasons unavailable to the subscription "
                            + "will be reported as restricted/skipped and must not be treated as successfully backfilled.",
                    footballData.getSeasons());
        } else {
            log.info("No explicit historical fixture backfill configured; fixture sync uses current seasons.");
        }
        log.info("Starting core-data bootstrap: league={}, team={}, fixture={}, standings={}",
                leagueProvider, teamProvider, fixtureProvider, standingsProvider);

        runStage("leagues", () -> leagueIngestionService.syncLeaguesForProvider(leagueProvider));
        runStage("teams", () -> teamIngestionService.syncTeamsForProvider(teamProvider));
        // Null date bounds import all fixtures for current seasons, or every configured season
        // when app.football-data.seasons is set. Persistence is idempotent via provider fixture IDs.
        runStage("fixtures", () -> fixtureIngestionService.syncFixturesForProvider(fixtureProvider, null, null));
        runStage("standings", () -> standingsIngestionService.syncStandingsForProvider(standingsProvider));

        log.info("Core-data bootstrap completed. Stage status: leagues=COMPLETED, teams=COMPLETED, "
                + "fixtures=COMPLETED, standings=COMPLETED. See per-stage counters for skipped/failed records.");
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

    private void validateProvider(Provider provider, boolean leagueResource) {
        if (provider == null) throw new IllegalStateException("An ingestion provider is not configured.");

        if (provider == Provider.API_FOOTBALL) {
            AppProperties.ApiFootball config = appProperties.getApiFootball();
            if (config == null || !config.isEnabled()) {
                throw new IllegalStateException("API-Football ingestion is disabled. Set app.ingestion.api-football.enabled=true.");
            }
            requireToken(config.getApiKey(), "API_FOOTBALL_API_KEY");
            if (leagueResource && (config.getLeagueIds() == null || config.getLeagueIds().isEmpty())) {
                throw new IllegalStateException("Set app.ingestion.api-football.league-ids to a comma-separated list of API-Football league IDs.");
            }
        } else if (provider == Provider.FOOTBALL_DATA) {
            AppProperties.FootballData config = appProperties.getFootballData();
            if (config == null || !config.isEnabled()) {
                throw new IllegalStateException("football-data.org ingestion is disabled. Set app.ingestion.football-data.enabled=true.");
            }
            requireToken(config.getApiKey(), "FOOTBALL_DATA_API_KEY");
            if (leagueResource && (config.getCompetitionIds() == null || config.getCompetitionIds().isEmpty())) {
                throw new IllegalStateException("Set app.ingestion.football-data.competition-ids to numeric football-data.org competition IDs.");
            }
        }
    }

    private void requireToken(String token, String variableName) {
        if (!StringUtils.hasText(token) || "mock".equalsIgnoreCase(token.trim())) {
            throw new IllegalStateException("Provider API token is not configured. Set " + variableName + ".");
        }
    }
}
