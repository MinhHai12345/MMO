package com.mmo.module.fb.ingest.job;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.job.AbstractJob;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.service.FixtureIngestionService;
import com.mmo.module.fb.ingest.service.LeagueIngestionService;
import com.mmo.module.fb.ingest.service.OddsIngestionService;
import com.mmo.module.fb.ingest.service.StandingsIngestionService;
import com.mmo.module.fb.ingest.service.TeamIngestionService;
import com.mmo.module.fb.ingest.service.impl.FootballDataUkSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;

/** Provider-neutral Quartz entry point for canonical football data synchronization. */
@Slf4j
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class FootballDataSyncJob extends AbstractJob<CronJob> {

    private final LeagueIngestionService leagueIngestionService;
    private final TeamIngestionService teamIngestionService;
    private final FixtureIngestionService fixtureIngestionService;
    private final StandingsIngestionService standingsIngestionService;
    private final OddsIngestionService oddsIngestionService;
    private final FootballDataUkSyncService footballDataUkSyncService;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        Provider leagueProvider = IngestionJobParameters.provider(context, "league");
        Provider teamProvider = IngestionJobParameters.provider(context, "team");
        Provider fixtureProvider = IngestionJobParameters.provider(context, "fixture");
        Provider standingsProvider = IngestionJobParameters.provider(context, "standings");
        Provider oddsProvider = IngestionJobParameters.provider(context, "odds");

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate recentFrom = today.minusDays(7);
        LocalDate upcomingTo = today.plusDays(14);

        log.info("Football data sync started: leagueProvider={}, teamProvider={}, fixtureProvider={}, standingsProvider={}, oddsProvider={}, range={}..{}",
                leagueProvider, teamProvider, fixtureProvider, standingsProvider, oddsProvider, recentFrom, upcomingTo);

        runStage("leagues", () -> {
            if (leagueProvider == null) leagueIngestionService.syncLeagues();
            else leagueIngestionService.syncLeaguesForProvider(leagueProvider);
        });
        checkStop();

        runStage("teams", () -> {
            if (teamProvider == null) teamIngestionService.syncTeams();
            else teamIngestionService.syncTeamsForProvider(teamProvider);
        });
        checkStop();

        runStage("recent-results", () -> {
            if (fixtureProvider == null) fixtureIngestionService.syncCurrentSeasonFixtures(recentFrom, today);
            else fixtureIngestionService.syncFixturesForProvider(fixtureProvider, recentFrom, today);
        });
        checkStop();

        runStage("upcoming-fixtures", () -> {
            if (fixtureProvider == null) fixtureIngestionService.syncCurrentSeasonFixtures(today, upcomingTo);
            else fixtureIngestionService.syncFixturesForProvider(fixtureProvider, today, upcomingTo);
        });
        checkStop();

        if (standingsProvider == Provider.UNDER_STATS) {
            log.info("Skipping standings: UNDER_STATS does not supply canonical standings");
        } else {
            runStage("standings", () -> {
                if (standingsProvider == null) standingsIngestionService.syncStandings();
                else standingsIngestionService.syncStandingsForProvider(standingsProvider);
            });
        }
        checkStop();

        runStage("upcoming-odds", () -> {
            if (oddsProvider == null) oddsIngestionService.syncUpcomingOdds();
            else oddsIngestionService.syncUpcomingOdds(oddsProvider);
        });
        checkStop();

        // Football-Data.co.uk is the single scheduled entry point for its CSV import.
        runStage("football-data-uk", footballDataUkSyncService::syncAllData);
        log.info("Football data sync completed");
    }

    private void runStage(String name, Runnable action) {
        long started = System.nanoTime();
        log.info("Football data stage STARTED: {}", name);
        action.run();
        log.info("Football data stage COMPLETED: {} durationMs={}",
                name, (System.nanoTime() - started) / 1_000_000);
    }

    private void checkStop() {
        if (shouldStop()) throw new IllegalStateException("Football data sync interrupted");
    }
}
