package com.mmo.module.fb.ingest.service.impl;

import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.service.FixtureIngestionService;
import com.mmo.module.fb.ingest.service.FootballDataSyncService;
import com.mmo.module.fb.ingest.service.LeagueIngestionService;
import com.mmo.module.fb.ingest.service.OddsIngestionService;
import com.mmo.module.fb.ingest.service.ProviderOverrides;
import com.mmo.module.fb.ingest.service.StandingsIngestionService;
import com.mmo.module.fb.ingest.service.TeamIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Coordinates provider-neutral ingestion use cases. Jobs and workflow orchestration never
 * call provider-specific services or adapters directly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FootballDataSyncServiceImpl implements FootballDataSyncService {

    private final AppProperties properties;
    private final LeagueIngestionService leagueIngestionService;
    private final TeamIngestionService teamIngestionService;
    private final FixtureIngestionService fixtureIngestionService;
    private final StandingsIngestionService standingsIngestionService;
    private final OddsIngestionService oddsIngestionService;
    private final FootballDataUkSyncService footballDataUkSyncService;

    @Override
    public void initializeProviderMappings() {
        footballDataUkSyncService.initialLeagueMappings();
    }

    @Override
    public void sync(ProviderOverrides overrides, Provider oddsProviderOverride) {
        ProviderOverrides selected = overrides == null ? ProviderOverrides.defaults() : overrides;
        AppProperties.Providers defaults = properties.getIngestion().getProviders();

        Provider league = selected.league() == null ? defaults.getLeague() : selected.league();
        Provider team = selected.team() == null ? defaults.getTeam() : selected.team();
        Provider fixture = selected.fixture() == null ? defaults.getFixture() : selected.fixture();
        Provider standings = selected.standings() == null ? defaults.getStandings() : selected.standings();
        Provider odds = oddsProviderOverride == null ? defaults.getOdds() : oddsProviderOverride;

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate recentFrom = today.minusDays(7);
        LocalDate upcomingTo = today.plusDays(14);

        log.info("Football data sync started: leagueProvider={}, teamProvider={}, fixtureProvider={}, "
                        + "standingsProvider={}, oddsProvider={}, recentRange={}..{}, upcomingRange={}..{}",
                league, team, fixture, standings, odds, recentFrom, today, today, upcomingTo);

        runStage("leagues", () -> leagueIngestionService.syncLeaguesForProvider(league));
        runStage("teams", () -> teamIngestionService.syncTeamsForProvider(team));
        runStage("recent-results", () -> fixtureIngestionService.syncFixturesForProvider(fixture, recentFrom, today));
        runStage("upcoming-fixtures", () -> fixtureIngestionService.syncFixturesForProvider(fixture, today, upcomingTo));

        if (standings == Provider.UNDER_STATS) {
            log.info("Skipping standings: UNDER_STATS does not expose canonical standings");
        } else {
            runStage("standings", () -> standingsIngestionService.syncStandingsForProvider(standings));
        }

        runStage("upcoming-odds", () -> oddsIngestionService.syncUpcomingOdds(odds));

        // CSV enrichment is a separate source with its own import checkpoint and mapping rules.
        runStage("football-data-uk", footballDataUkSyncService::syncAllData);
        log.info("Football data sync completed");
    }

    private void runStage(String name, Runnable operation) {
        long started = System.nanoTime();
        log.info("Football data stage STARTED: {}", name);
        operation.run();
        log.info("Football data stage COMPLETED: {} durationMs={}",
                name, (System.nanoTime() - started) / 1_000_000);
    }
}
