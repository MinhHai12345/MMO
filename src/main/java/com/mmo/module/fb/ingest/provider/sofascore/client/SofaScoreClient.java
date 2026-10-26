package com.mmo.module.fb.ingest.provider.sofascore.client;

import com.microsoft.playwright.Page;
import com.mmo.module.fb.team.entity.Team;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaDailyMatchWrapper;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaScoreResponseDTO;

import java.util.List;
import java.util.Map;

public interface SofaScoreClient {

    List<SofaScoreResponseDTO.UniqueTournamentDTO> fetchLeagues(Page page);

    List<SofaScoreResponseDTO.SeasonDTO> fetchSeasonByTournamentId(Long sofaTournamentId, Page page);

    List<SofaScoreResponseDTO.StandingGroupDTO> fetchStandingTotal(Long sofaTournamentId, Long sofaSeasonId, Page page);

    List<SofaScoreResponseDTO.EventDTO> fetchMatchesByRound(Long sofaTournamentId, Long sofaSeasonId, int round, Page page);

    SofaScoreResponseDTO.MatchesResponseDTO fetchSeasonMatchesPage(
            Long sofaTournamentId, Long sofaSeasonId, String direction, int pageIndex, Page page);

    List<SofaScoreResponseDTO.PeriodStatisticsDTO> fetchMatchStatistics(Long sofaMatchId, Page page);

    Map<Long, SofaScoreResponseDTO.MatchOddDetailDTO> fetchDailyMatchOdds(Page page);

    List<SofaScoreResponseDTO.EventDTO> fetchMatchesDailyByTournamentId(Long sofaTournamentId, Page page);

    List<SofaScoreResponseDTO.EventDTO> fetchHistoriesMatchesByTeamIdAndIndex(Team team, int index, Page page);

    SofaScoreResponseDTO.EventDTO fetchMatchById(Long sofaMatchId, Page page);

    SofaDailyMatchWrapper fetchMatchesDaily(Long sofaTournamentId, Page page);
}
