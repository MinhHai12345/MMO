package com.mmo.module.fb.crawler.service.impl;

import com.microsoft.playwright.Page;
import com.mmo.configuration.AppProperties;
import com.mmo.module.fb.crawler.model.sofa.SofaMatchData;
import com.mmo.module.fb.crawler.model.sofa.SofaMatchStatisticsData;
import com.mmo.module.fb.crawler.model.sofa.SofaOddsData;
import com.mmo.module.fb.crawler.model.sofa.SofaSeasonData;
import com.mmo.module.fb.crawler.model.sofa.SofaStandingsData;
import com.mmo.module.fb.crawler.model.sofa.SofaUniqueTournamentsData;
import com.mmo.module.fb.crawler.service.AbstractCrawlerService;
import com.mmo.module.fb.crawler.service.SofaCrawlerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SofaCrawlerServiceImpl extends AbstractCrawlerService implements SofaCrawlerService {
    private final AppProperties appProperties;

    private static final String FETCH_ALL_UNIQUE_TOURNAMENTS_URI = "config/default-unique-tournaments/VN/football";
    private static final String FETCH_SEASON_BY_TOURNAMENT_URI = "%sunique-tournament/%d/seasons";
    private static final String FETCH_STANDINGS_TOTAL_BY_TOURNAMENT_AND_SEASON_URI = "%sunique-tournament/%d/season/%d/standings/total";
    private static final String FETCH_MATCH_BY_ROUND_URI = "%sunique-tournament/%d/season/%d/events/round/%d";
    private static final String FETCH_MATCH_STATISTICS_URI = "%sevent/%d/statistics";
    private static final String DAILY_ALL_MATCH_ODDS_URI = "%ssport/football/odds/1/%s";
    private static final String MATCH_UPCOMING_BY_TEAM_AND_DATE_URI = "%sunique-tournament/%d/scheduled-events/%s";

//    private static final String MATCH_ODDS_URI = "%sevent/%d/odds/1/all";
//    private static final String DAILY_MATCH_UP_COMING_URI = "%sodds/1/featured-events-by-popularity/football";


    @Override
    public List<SofaUniqueTournamentsData.UniqueTournamentDTO> fetchLeagues(Page page) {
        String url = appProperties.getSofaScore().getApi().concat(FETCH_ALL_UNIQUE_TOURNAMENTS_URI);
        SofaUniqueTournamentsData tournamentsResponse = safeFetch(url, page, SofaUniqueTournamentsData.class);
        return tournamentsResponse != null ? tournamentsResponse.getUniqueTournaments() : Collections.emptyList();
    }

    @Override
    public List<SofaSeasonData.SeasonDTO> fetchSeasonByTournamentId(Long sofaTournamentId, Page page) {
        String url = String.format(FETCH_SEASON_BY_TOURNAMENT_URI, appProperties.getSofaScore().getApi(), sofaTournamentId);
        SofaSeasonData seasonData = safeFetch(url, page, SofaSeasonData.class);
        return seasonData != null ? seasonData.getSeasons() : Collections.emptyList();
    }

    @Override
    public List<SofaStandingsData.StandingGroupDTO> fetchStandingTotal(Long sofaTournamentId, Long sofaSeasonId, Page page) {
        String url = String.format(FETCH_STANDINGS_TOTAL_BY_TOURNAMENT_AND_SEASON_URI,
                appProperties.getSofaScore().getApi(), sofaTournamentId, sofaSeasonId);
        SofaStandingsData sofaStandingsData = safeFetch(url, page, SofaStandingsData.class);
        return sofaStandingsData != null ? sofaStandingsData.getStandings() : Collections.emptyList();
    }

    @Override
    public List<SofaMatchData.SofaEventDTO> fetchMatchesByRound(Long sofaTournamentId, Long sofaSeasonId, int round, Page page) {
        String url = String.format(FETCH_MATCH_BY_ROUND_URI, appProperties.getSofaScore().getApi(),
                sofaTournamentId, sofaSeasonId, round);
        SofaMatchData matchData = safeFetch(url, page, SofaMatchData.class);
        return matchData != null ? matchData.getEvents() : Collections.emptyList();
    }

    @Override
    public List<SofaMatchStatisticsData.PeriodStatisticsDTO> fetchMatchStatistics(Long sofaMatchId, Page page) {
        String url = String.format(FETCH_MATCH_STATISTICS_URI, appProperties.getSofaScore().getApi(), sofaMatchId);
        SofaMatchStatisticsData matchData = safeFetch(url, page, SofaMatchStatisticsData.class);
        return matchData != null ? matchData.getStatistics() : Collections.emptyList();
    }

    @Override
    public Map<String, SofaOddsData.MatchOddDetailDTO> fetchDailyMatchOdds(Page page) {
        String url = String.format(DAILY_ALL_MATCH_ODDS_URI, appProperties.getSofaScore().getApi(), LocalDate.now());
        SofaOddsData oddsData = safeFetch(url, page, SofaOddsData.class);
        return oddsData != null ? oddsData.getOdds() : Collections.emptyMap();
    }

    @Override
    public List<SofaMatchData.SofaEventDTO> fetchMatchesDailyByTournamentId(Long sofaTournamentId, Page page) {
        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String url = String.format(MATCH_UPCOMING_BY_TEAM_AND_DATE_URI, appProperties.getSofaScore().getApi(),
                sofaTournamentId, today);
        SofaMatchData matchData = safeFetch(url, page, SofaMatchData.class);
        return matchData != null ? matchData.getEvents() : Collections.emptyList();
    }

}
