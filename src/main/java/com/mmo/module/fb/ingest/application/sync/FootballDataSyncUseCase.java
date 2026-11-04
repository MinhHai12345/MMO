package com.mmo.module.fb.ingest.application.sync;

import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.application.lock.IngestionDistributedLock;
import com.mmo.module.fb.ingest.application.port.out.FootballDataUkIngestionPort;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.service.FixtureIngestionService;
import com.mmo.module.fb.ingest.service.FootballDataSyncService;
import com.mmo.module.fb.ingest.service.LeagueIngestionService;
import com.mmo.module.fb.ingest.service.OddsIngestionService;
import com.mmo.module.fb.ingest.service.ProviderOverrides;
import com.mmo.module.fb.ingest.service.StandingsIngestionService;
import com.mmo.module.fb.ingest.service.SeasonIngestionService;
import com.mmo.module.fb.ingest.service.TeamIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Coordinates provider-neutral ingestion use cases. Jobs and workflow orchestration never
 * call provider-specific services or adapters directly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FootballDataSyncUseCase implements FootballDataSyncService {

    private final IngestionDistributedLock distributedLock;
    private final AppProperties properties;
    private final LeagueIngestionService leagueIngestionService;
    private final SeasonIngestionService seasonIngestionService;
    private final TeamIngestionService teamIngestionService;
    private final FixtureIngestionService fixtureIngestionService;
    private final StandingsIngestionService standingsIngestionService;
    private final OddsIngestionService oddsIngestionService;
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
        ProviderOverrides selected = overrides == null ? ProviderOverrides.defaults() : overrides;
        AppProperties.Providers defaults = properties.getIngestion().getProviders();

        Provider league = selected.league() == null ? defaults.getLeague() : selected.league();
        Provider season = selected.season() == null ? defaults.getSeason() : selected.season();
        Provider team = selected.team() == null ? defaults.getTeam() : selected.team();
        Provider fixture = selected.fixture() == null ? defaults.getFixture() : selected.fixture();
        Provider standings = selected.standings() == null ? defaults.getStandings() : selected.standings();
        Provider odds = oddsProviderOverride == null ? defaults.getOdds() : oddsProviderOverride;

        requireFootballDataProvider(league, "league");
        requireFootballDataProvider(season, "season");
        requireFootballDataProvider(team, "team");
        requireFootballDataProvider(fixture, "fixture");
        requireFootballDataProvider(standings, "standings");

        log.info("Football data incremental sync started for the current active season only: "
                        + "leagueProvider={}, teamProvider={}, fixtureProvider={}, standingsProvider={}, oddsProvider={}",
                league, team, fixture, standings, odds);

        Set<Provider> catalogueProviders = new LinkedHashSet<>(
                List.of(league, season, team, fixture));
        if (standings != Provider.UNDER_STATS) catalogueProviders.add(standings);
        for (Provider provider : catalogueProviders) {
            runStage("leagues/" + provider,
                    () -> leagueIngestionService.syncLeaguesForProvider(provider));
        }
        // Team and fixture fetches resolve provider-specific season IDs, not the
        // external season ID belonging to an unrelated catalogue provider.
        for (Provider provider : catalogueProviders) {
            runStage("seasons/" + provider,
                    () -> seasonIngestionService.syncSeasonsForProvider(provider));
        }
        runStage("teams/" + team, () -> teamIngestionService.syncTeamsForProvider(team));
        runStage("current-season-fixtures",
                () -> fixtureIngestionService.syncFixturesForProvider(fixture, null, null));

        if (standings == Provider.UNDER_STATS) {
            log.info("Skipping standings: UNDER_STATS does not expose canonical standings");
        } else {
            runStage("standings", () -> standingsIngestionService.syncStandingsForProvider(standings));
        }

        if (odds == null) {
            log.info("Skipping upcoming odds: no active odds provider is configured");
        } else if (!isProviderEnabled(odds)) {
            log.warn("Skipping upcoming odds because selected provider {} is disabled or not configured", odds);
        } else {
            runStage("upcoming-odds/" + odds, () -> oddsIngestionService.syncUpcomingOdds(odds));
        }

        // The initial 2014+ CSV backfill belongs to FootballDataBootstrapService.
        // The frequent sync job must fetch only the current Football-Data UK season.
        runStage("football-data-uk-current-season", footballDataUkSyncService::syncCurrentSeasonData);
        log.info("Football data sync completed");
    }

    private void requireFootballDataProvider(Provider provider, String resource) {
        if (provider != Provider.FOOTBALL_DATA) {
            throw new IllegalStateException("Phase 1 supports FOOTBALL_DATA for " + resource
                    + " ingestion; " + provider + " is temporarily disabled. "
                    + "Football-Data UK is orchestrated through its dedicated CSV workflow.");
        }
    }

    private boolean isProviderEnabled(Provider provider) {
        AppProperties.ProviderConfig config = properties.getIngestion().getProviderConfig().get(provider);
        return config != null && config.isEnabled();
    }

    private void runStage(String name, Runnable operation) {
        long started = System.nanoTime();
        log.info("Football data stage STARTED: {}", name);
        operation.run();
        log.info("Football data stage COMPLETED: {} durationMs={}",
                name, (System.nanoTime() - started) / 1_000_000);
    }
}
