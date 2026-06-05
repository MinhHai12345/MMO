package com.mmo.module.fb.crawler.service;

import com.microsoft.playwright.Page;
import com.mmo.module.fb.crawler.model.sofa.SofaMatchesData;
import com.mmo.module.fb.crawler.model.sofa.SofaMatchStatisticsData;
import com.mmo.module.fb.crawler.model.sofa.SofaOddsData;
import com.mmo.module.fb.crawler.model.sofa.SofaSeasonData;
import com.mmo.module.fb.crawler.model.sofa.SofaStandingsData;
import com.mmo.module.fb.crawler.model.sofa.SofaUniqueTournamentsData;

import java.util.List;
import java.util.Map;

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
    List<SofaMatchesData.SofaEventDTO> fetchMatchesByRound(Long sofaTournamentId, Long sofaSeasonId, int round, Page page);

    /**
     * Get match Statistics by match id
     */
    List<SofaMatchStatisticsData.PeriodStatisticsDTO> fetchMatchStatistics(Long sofaMatchId, Page page);

    /**
     * Get all match odds by today
     */
    Map<Long, SofaOddsData.MatchOddDetailDTO> fetchDailyMatchOdds(Page page);

    /**
     * Fetch Match Daily by tournament id
     */
    List<SofaMatchesData.SofaEventDTO> fetchMatchesDailyByTournamentId(Long sofaTournamentId, Page page);

    /**
     * Fetch histories match by team id and index
     */
    List<SofaMatchesData.SofaEventDTO> fetchHistoriesMatchesByTeamIdAndIndex(Long sofaTeamId, int index, Page page);

    /**
     * Fetch Match By ID
     */
    SofaMatchesData.SofaEventDTO fetchMatchById(Long sofaMatchId, Page page);

}
