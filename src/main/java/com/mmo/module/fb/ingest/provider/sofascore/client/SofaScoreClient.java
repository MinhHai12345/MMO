package com.mmo.module.fb.ingest.provider.sofascore.client;

import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaScoreResponseDTO;

import com.microsoft.playwright.Page;
import com.mmo.module.fb.team.entity.Team;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaDailyMatchWrapper;

import java.util.List;
import java.util.Map;

public interface SofaScoreClient {

    /**
     * Fetch all football tournaments
     */
    List<SofaScoreResponseDTO.UniqueTournamentDTO> fetchLeagues(Page page);

    /**
     * Fetch season by tournament id
     */
    List<SofaScoreResponseDTO.SeasonDTO> fetchSeasonByTournamentId(Long sofaTournamentId, Page page);


    /**
     * Fetch standing total by tournament id and season id
     */
    List<SofaScoreResponseDTO.StandingGroupDTO> fetchStandingTotal(Long sofaTournamentId, Long sofaSeasonId, Page page);

    /**
     * Fetch Match by tournament id and season id and round
     */
    List<SofaScoreResponseDTO.EventDTO> fetchMatchesByRound(Long sofaTournamentId, Long sofaSeasonId, int round, Page page);

    /**
     * Fetch a paginated set of finished or upcoming events for a tournament season.
     * Direction must be either "last" or "next".
     */
    SofaScoreResponseDTO.MatchesResponseDTO fetchSeasonMatchesPage(
            Long sofaTournamentId, Long sofaSeasonId, String direction, int pageIndex, Page page);

    /**
     * Get match Statistics by match id
     */
    List<SofaScoreResponseDTO.PeriodStatisticsDTO> fetchMatchStatistics(Long sofaMatchId, Page page);

    /**
     * Get all match odds by today
     */
    Map<Long, SofaScoreResponseDTO.MatchOddDetailDTO> fetchDailyMatchOdds(Page page);

    /**
     * Fetch Match Daily by tournament id
     */
    List<SofaScoreResponseDTO.EventDTO> fetchMatchesDailyByTournamentId(Long sofaTournamentId, Page page);

    /**
     * Fetch histories match by team id and index
     */
    List<SofaScoreResponseDTO.EventDTO> fetchHistoriesMatchesByTeamIdAndIndex(Team team, int index, Page page);

    /**
     * Fetch Match By ID
     */
    SofaScoreResponseDTO.EventDTO fetchMatchById(Long sofaMatchId, Page page);

    /**
     * Fetch Match Daily by tournament id
     */
    SofaDailyMatchWrapper fetchMatchesDaily(Long sofaTournamentId, Page page);

}
