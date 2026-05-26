package com.mmo.module.fb.crawler.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class SofaCrawlerServiceImpl extends AbstractCrawlerService implements SofaCrawlerService {
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

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
        try {
            String url = appProperties.getSofaScore().getApi().concat(FETCH_ALL_UNIQUE_TOURNAMENTS_URI);
            randomDelay();
            Response response = page.navigate(url);
            if (response.status() == 200) {
                SofaUniqueTournamentsData tournamentsResponse = objectMapper.readValue(response.text(), SofaUniqueTournamentsData.class);
                if (tournamentsResponse != null) {
                    return tournamentsResponse.getUniqueTournaments();
                }
            }
        } catch (Exception e) {
            log.error("❌ Fetch all unique tournaments error: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    @Override
    public List<SofaSeasonData.SeasonDTO> fetchSeasonByTournamentId(Long sofaTournamentId, Page page) {
        try {
            String url = String.format(FETCH_SEASON_BY_TOURNAMENT_URI, appProperties.getSofaScore().getApi(), sofaTournamentId);
            randomDelay();
            Response response = page.navigate(url);
            if (response != null && response.status() == 200) {
                SofaSeasonData seasonData = objectMapper.readValue(response.text(), SofaSeasonData.class);
                if (seasonData != null) {
                    return seasonData.getSeasons();
                }
            }
        } catch (Exception e) {
            log.error("❌ Fetch season for tournament id {} error: {}", sofaTournamentId, e.getMessage());
        }
        return Collections.emptyList();
    }

    @Override
    public List<SofaStandingsData.StandingGroupDTO> fetchStandingTotal(Long sofaTournamentId, Long sofaSeasonId, Page page) {
        try {
            randomDelay();
            String url = String.format(FETCH_STANDINGS_TOTAL_BY_TOURNAMENT_AND_SEASON_URI,
                    appProperties.getSofaScore().getApi(), sofaTournamentId, sofaTournamentId);
            Response response = page.navigate(url);
            if (response.status() == 200) {
                SofaStandingsData sofaStandingsData = objectMapper.readValue(response.text(), SofaStandingsData.class);
                if (sofaStandingsData != null) {
                    return sofaStandingsData.getStandings();
                }
            }
        } catch (Exception e) {
            log.error("❌ Fetch standings total by tournament id {} and season id {} error: {}",
                    sofaTournamentId, sofaSeasonId, e.getMessage());
        }
        return Collections.emptyList();
    }

    @Override
    public List<SofaMatchData.SofaEventDTO> fetchMatchesByRound(Long sofaTournamentId, Long sofaSeasonId, int round, Page page) {
        try {
            randomDelay();
            String url = String.format(FETCH_MATCH_BY_ROUND_URI, appProperties.getSofaScore().getApi(),
                    sofaTournamentId, sofaSeasonId, round);
            Response response = page.navigate(url);
            if (response != null && response.status() == 200) {
                SofaMatchData matchData = objectMapper.readValue(response.text(), SofaMatchData.class);
                if (matchData != null) {
                    return matchData.getEvents();
                }
            }
        } catch (Exception e) {
            log.error("❌ Fetch Match of Round {}, tournament id {}, season id {} error: {}", round, sofaTournamentId, sofaSeasonId, e.getMessage());
        }
        return Collections.emptyList();
    }

    @Override
    public List<SofaMatchStatisticsData.PeriodStatisticsDTO> fetchMatchStatistics(Long sofaMatchId, Page page) {
        try {
            randomDelay();
            String url = String.format(FETCH_MATCH_STATISTICS_URI, appProperties.getSofaScore().getApi(), sofaMatchId);
            Response response = page.navigate(url);
            if (response != null && response.status() == 200) {
                SofaMatchStatisticsData matchData = objectMapper.readValue(response.text(), SofaMatchStatisticsData.class);
                if (matchData != null) {
                    return matchData.getStatistics();
                }
            }
        } catch (Exception e) {
            log.error("❌ Fetch Match Statistics of match id {} error: {}", sofaMatchId, e.getMessage());
        }
        return Collections.emptyList();
    }

    @Override
    public SofaOddsData fetchDailyMatchOdds(Page page) {
        try {
            randomDelay();
            String url = String.format(DAILY_ALL_MATCH_ODDS_URI, appProperties.getSofaScore().getApi(), LocalDate.now());
            Response response = page.navigate(url);
            if (response.status() == 200) {
                return objectMapper.readValue(response.text(), SofaOddsData.class);
            }
        } catch (Exception e) {
            log.error("❌ Fetch all match odds daily error: {}", e.getMessage());
        }
        return null;
    }

    @Override
    public List<SofaMatchData.SofaEventDTO> fetchMatchesDailyByTournamentId(Long sofaTournamentId, Page page) {
        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        try {
            randomDelay();
            String url = String.format(MATCH_UPCOMING_BY_TEAM_AND_DATE_URI, appProperties.getSofaScore().getApi(),
                    sofaTournamentId, today);
            Response response = page.navigate(url);
            if (response != null && response.status() == 200) {
                SofaMatchData matchData = objectMapper.readValue(response.text(), SofaMatchData.class);
                if (matchData != null) {
                    return matchData.getEvents();
                }
            }
        } catch (Exception e) {
            log.error("❌ Fetch Match Daily by tournament id {}, today {} error: {}", sofaTournamentId, today, e.getMessage());
        }
        return Collections.emptyList();
    }

}
