package com.mmo.module.fb.ingest.provider.strategy.impl;

import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.ingest.provider.model.sofa.SofaMatchesData;
import com.mmo.module.fb.ingest.provider.strategy.AbstractCrawler;
import com.mmo.module.fb.ingest.service.FixtureIngestionService;
import com.mmo.module.fb.ingest.service.LeagueIngestionService;
import com.mmo.module.fb.ingest.service.StandingsIngestionService;
import com.mmo.module.fb.ingest.service.TeamIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * Compatibility adapter for existing Quartz jobs.
 * Source-specific HTTP and DTO logic lives in provider/apifootball; persistence is handled by
 * ingestion services. New jobs should depend on those services rather than this legacy strategy.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiFootballCrawlStrategy extends AbstractCrawler {

    private final LeagueIngestionService leagueIngestionService;
    private final TeamIngestionService teamIngestionService;
    private final FixtureIngestionService fixtureIngestionService;
    private final StandingsIngestionService standingsIngestionService;

    @Override
    public void syncLeagues() {
        leagueIngestionService.syncLeagues();
    }

    @Override
    public void storeSeasons() {
        // League sync also upserts the seasons returned by the source.
        syncLeagues();
    }

    @Override
    public void syncTeams() {
        teamIngestionService.syncTeams();
    }

    @Override
    public void storeMatches() {
        // Lightweight rolling sync for the daily job.
        fixtureIngestionService.syncCurrentSeasonFixtures(
                LocalDate.now().minusDays(14), LocalDate.now().plusDays(30));
    }

    @Override
    public void syncAllCurrentSeasonFixtures() {
        // Bootstrap/reconciliation job: no date filter includes completed and future fixtures.
        fixtureIngestionService.syncCurrentSeasonFixtures(null, null);
    }

    @Override
    public void storeMatchStatistics() {
        log.info("Match statistics sync is not implemented in this phase.");
    }

    @Override
    public void prepareMatchUpcomingDaily() {
        fixtureIngestionService.syncCurrentSeasonFixtures(LocalDate.now(), LocalDate.now().plusDays(2));
    }

    @Override
    public void prepareMatchResultDaily() {
        fixtureIngestionService.syncCurrentSeasonFixtures(LocalDate.now().minusDays(3), LocalDate.now());
    }

    public void syncStandings() {
        standingsIngestionService.syncStandings();
    }

    @Override
    public List<SofaMatchesData.SofaEventDTO> getLatestHistoriesMatchesByTeamId(Long teamId) {
        return Collections.emptyList();
    }

    @Override
    public Provider getProvider() {
        return Provider.API_FOOTBALL;
    }
}
