package com.mmo.module.fb.crawler.service;

import com.microsoft.playwright.Page;
import com.mmo.module.fb.crawler.model.sofa.SofaMatchData;
import com.mmo.module.fb.crawler.model.sofa.SofaMatchStatisticsData;
import com.mmo.module.fb.crawler.model.sofa.SofaOddsData;
import com.mmo.module.fb.crawler.model.sofa.SofaSeasonData;
import com.mmo.module.fb.crawler.model.sofa.SofaStandingsData;
import com.mmo.module.fb.crawler.model.sofa.SofaUniqueTournamentsData;

import java.util.List;

public interface SofaCrawlerService {

    /**
     * Fetch all football tournaments
     */
    List<SofaUniqueTournamentsData.UniqueTournamentDTO> fetchLeagues(Page page);

    /**
     * Fetch season by tournament id
     */
    List<SofaSeasonData.SeasonDTO> fetchSeasonByTournamentId(Long sofaTournamentId, Page page);


    /**
     * Fetch standing total by tournament id and season id
     */
    List<SofaStandingsData.StandingGroupDTO> fetchStandingTotal(Long sofaTournamentId, Long sofaSeasonId, Page page);

    /**
     * Fetch Match by tournament id and season id and round
     */
    List<SofaMatchData.SofaEventDTO> fetchMatchesByRound(Long sofaTournamentId, Long sofaSeasonId, int round, Page page);

    /**
     * Get match Statistics by match id
     */
    List<SofaMatchStatisticsData.PeriodStatisticsDTO> fetchMatchStatistics(Long sofaMatchId, Page page);

    /**
     * Get all match odds by today
     */
    SofaOddsData fetchDailyMatchOdds(Page page);

    /**
     * Fetch Match Daily by tournament id
     */
    List<SofaMatchData.SofaEventDTO> fetchMatchesDailyByTournamentId(Long sofaTournamentId, Page page);

}
