package com.mmo.module.fb.crawler.service.impl;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.mmo.configuration.AppProperties;
import com.mmo.module.fb.crawler.model.DynamicFetchResult;
import com.mmo.module.fb.crawler.model.sofa.SofaDailyMatchWrapper;
import com.mmo.module.fb.crawler.model.sofa.SofaMatchData;
import com.mmo.module.fb.crawler.model.sofa.SofaMatchStatisticsData;
import com.mmo.module.fb.crawler.model.sofa.SofaMatchesData;
import com.mmo.module.fb.crawler.model.sofa.SofaOddsData;
import com.mmo.module.fb.crawler.model.sofa.SofaSeasonData;
import com.mmo.module.fb.crawler.model.sofa.SofaStandingsData;
import com.mmo.module.fb.crawler.model.sofa.SofaUniqueTournamentsData;
import com.mmo.module.fb.crawler.service.AbstractCrawlerService;
import com.mmo.module.fb.crawler.service.SofaCrawlerService;
import com.mmo.module.fb.entity.Team;
import com.mmo.utils.DateTimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
    private static final String FETCH_DAILY_ALL_MATCH_ODDS_URI = "/api/v1/sport/football/odds/1/%s";
    private static final String FETCH_MATCH_UPCOMING_BY_TEAM_AND_DATE_URI = "/api/v1/unique-tournament/%d/scheduled-events/%s";
    private static final String FETCH_MATCH_HISTORIES_BY_TEAM_ID_AND_INDEX_URI = "team/%d/events/last/%d";
    private static final String FETCH_MATCH_HISTORIES_BY_TEAM_ID_AND_INDEX_PAGE = "%s/football/team/%s/%d";
    private static final String FETCH_MATCH_BY_ID_URI = "%sevent/%d";

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
    public List<SofaMatchesData.SofaEventDTO> fetchMatchesByRound(Long sofaTournamentId, Long sofaSeasonId, int round, Page page) {
        String url = String.format(FETCH_MATCH_BY_ROUND_URI, appProperties.getSofaScore().getApi(),
                sofaTournamentId, sofaSeasonId, round);
        SofaMatchesData matchData = safeFetch(url, page, SofaMatchesData.class);
        return matchData != null ? matchData.getEvents() : Collections.emptyList();
    }

    @Override
    public List<SofaMatchStatisticsData.PeriodStatisticsDTO> fetchMatchStatistics(Long sofaMatchId, Page page) {
        String url = String.format(FETCH_MATCH_STATISTICS_URI, appProperties.getSofaScore().getApi(), sofaMatchId);
        SofaMatchStatisticsData matchData = safeFetch(url, page, SofaMatchStatisticsData.class);
        return matchData != null ? matchData.getStatistics() : Collections.emptyList();
    }

    @Override
    public Map<Long, SofaOddsData.MatchOddDetailDTO> fetchDailyMatchOdds(Page page) {
        String endpoint = String.format(FETCH_DAILY_ALL_MATCH_ODDS_URI, DateTimeUtils.today());
        SofaOddsData oddsData = safeFetch(appProperties.getSofaScore().getUrl(), page, SofaOddsData.class);
        return oddsData != null ? oddsData.getOdds() : Collections.emptyMap();
    }

    @Override
    public List<SofaMatchesData.SofaEventDTO> fetchMatchesDailyByTournamentId(Long sofaTournamentId, Page page) {
        String endpoint = String.format(FETCH_MATCH_UPCOMING_BY_TEAM_AND_DATE_URI, sofaTournamentId, DateTimeUtils.today());
        SofaMatchesData matchData = safeFetch(appProperties.getSofaScore().getUrl(), page, SofaMatchesData.class);
        return matchData != null ? matchData.getEvents() : Collections.emptyList();
    }

    @Override
    public List<SofaMatchesData.SofaEventDTO> fetchHistoriesMatchesByTeamIdAndIndex(Team team, int index, Page page) {
        String loadPageUrl = String.format(FETCH_MATCH_HISTORIES_BY_TEAM_ID_AND_INDEX_PAGE, appProperties.getSofaScore().getUrl(),
                team.getSlug(), team.getSofaScoreId());
        String historiesEndpoint = String.format(FETCH_MATCH_HISTORIES_BY_TEAM_ID_AND_INDEX_URI, team.getSofaScoreId(), index);
        Map<String, Class<?>> configMap = Map.of(historiesEndpoint, SofaMatchesData.class);

        DynamicFetchResult result = safeFetch(loadPageUrl, configMap, page, null);
        List<SofaMatchesData.SofaEventDTO> events = new ArrayList<>();
        if (result.hasData(historiesEndpoint)) {
            SofaMatchesData matchData = result.get(historiesEndpoint, SofaMatchesData.class);
            if (matchData != null) return matchData.getEvents();
        }

        return events;
    }

    @Override
    public SofaMatchesData.SofaEventDTO fetchMatchById(Long sofaMatchId, Page page) {
        String url = String.format(FETCH_MATCH_BY_ID_URI, appProperties.getSofaScore().getApi(), sofaMatchId);
        SofaMatchData matchData = safeFetch(url, page, SofaMatchData.class);
        return matchData != null ? matchData.getEvent() : null;
    }

    @Override
    public SofaDailyMatchWrapper fetchMatchesDaily(Long sofaTournamentId, Page page) {
        String loadPageUrl = appProperties.getSofaScore().getUrl();
        String matchEndpoint = String.format(FETCH_MATCH_UPCOMING_BY_TEAM_AND_DATE_URI, sofaTournamentId, DateTimeUtils.today());
        String oddsEndpoint = String.format(FETCH_DAILY_ALL_MATCH_ODDS_URI, DateTimeUtils.today());
        Map<String, Class<?>> configMap = Map.of(matchEndpoint, SofaMatchesData.class, oddsEndpoint, SofaOddsData.class);

        DynamicFetchResult result = safeFetch(loadPageUrl, configMap, page, (p) -> {
            try {
                log.info("⏳ Đang chờ bộ khung giao diện chứa nút Odds đính vào DOM...");

                Locator oddsText = p.locator("span:has-text('Odds'), div:has-text('Odds')").first();
                oddsText.waitFor(new Locator.WaitForOptions()
                        .setState(com.microsoft.playwright.options.WaitForSelectorState.ATTACHED)
                        .setTimeout(15000));

                String jsForceClick = """
                        () => {
                            const input = document.querySelector("input[role='switch']");
                            if (input) {
                                const target = input.nextElementSibling || input.closest('label') || input;
                                target.click();
                                return 'CLICKED_SLIDER';
                            }
                            return null;
                        }
                        """;
                p.evaluate(jsForceClick);
            } catch (TimeoutError te) {
                log.error("❌ [Trigger Timeout] Quá 15 giây không thấy cấu trúc chứa nút Odds xuất hiện.");
            } catch (Exception ex) {
                log.error("❌ Lỗi xử lý trong Trigger: {}", ex.getMessage());
            }
        });

        List<SofaMatchesData.SofaEventDTO> events = new ArrayList<>();
        Map<Long, SofaOddsData.MatchOddDetailDTO> oddsMap = new HashMap<>();

        if (result.hasData(matchEndpoint)) {
            SofaMatchesData matchData = result.get(matchEndpoint, SofaMatchesData.class);
            if (matchData != null) events = matchData.getEvents();
        }

        if (result.hasData(oddsEndpoint)) {
            SofaOddsData oddsData = result.get(oddsEndpoint, SofaOddsData.class);
            if (oddsData != null) oddsMap = oddsData.getOdds();
        }
        return new SofaDailyMatchWrapper(events, oddsMap);
    }

}
