package com.mmo.module.fb.ingest.provider.sofascore.client;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.mmo.AppProperties;
import com.mmo.core.utils.DateTimeUtils;
import com.mmo.module.fb.team.entity.Team;
import com.mmo.module.fb.team.entity.TeamExternalId;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.team.repository.TeamExternalIdRepository;
import com.mmo.module.fb.ingest.provider.model.DynamicFetchResult;
import com.mmo.module.fb.ingest.provider.model.sofa.SofaDailyMatchWrapper;
import com.mmo.module.fb.ingest.provider.model.sofa.SofaMatchData;
import com.mmo.module.fb.ingest.provider.model.sofa.SofaMatchStatisticsData;
import com.mmo.module.fb.ingest.provider.model.sofa.SofaMatchesData;
import com.mmo.module.fb.ingest.provider.model.sofa.SofaOddsData;
import com.mmo.module.fb.ingest.provider.model.sofa.SofaSeasonData;
import com.mmo.module.fb.ingest.provider.model.sofa.SofaStandingsData;
import com.mmo.module.fb.ingest.provider.model.sofa.SofaUniqueTournamentsData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SofaScoreClientImpl extends AbstractSofaScoreClient implements SofaScoreClient {
    private final AppProperties appProperties;
    private final TeamExternalIdRepository teamExternalIdRepository;

    private static final String FETCH_ALL_UNIQUE_TOURNAMENTS_URI = "config/default-unique-tournaments/%s/football";
    private static final String FETCH_SEASON_BY_TOURNAMENT_URI = "%sunique-tournament/%d/seasons";
    private static final String FETCH_STANDINGS_TOTAL_BY_TOURNAMENT_AND_SEASON_URI = "%sunique-tournament/%d/season/%d/standings/total";
    private static final String FETCH_MATCH_BY_ROUND_URI = "%sunique-tournament/%d/season/%d/events/round/%d";
    private static final String FETCH_MATCH_STATISTICS_URI = "%sevent/%d/statistics";
    private static final String FETCH_DAILY_ALL_MATCH_ODDS_URI = "/api/v1/sport/football/odds/1/%s";
    private static final String FETCH_MATCH_UPCOMING_BY_TEAM_AND_DATE_URI = "/api/v1/unique-tournament/%d/scheduled-events/%s";
    private static final String FETCH_MATCH_HISTORIES_BY_TEAM_ID_AND_INDEX_URI = "team/%s/events/last/%d";
    private static final String FETCH_MATCH_HISTORIES_BY_TEAM_ID_AND_INDEX_PAGE = "%s/football/team/%s/%s";
    private static final String FETCH_MATCH_BY_ID_URI = "%sevent/%d";

    @Override
    public List<SofaUniqueTournamentsData.UniqueTournamentDTO> fetchLeagues(Page page) {
        String region = appProperties.getSofaScore().getTournamentRegion();
        if (region == null || !region.matches("[A-Za-z]{2}")) {
            region = "EN";
        }
        String url = appProperties.getSofaScore().getApi()
                .concat(String.format(FETCH_ALL_UNIQUE_TOURNAMENTS_URI, region.toUpperCase(java.util.Locale.ROOT)));
        SofaUniqueTournamentsData tournamentsResponse = safeFetch(url, page, SofaUniqueTournamentsData.class);
        if (tournamentsResponse == null) {
            throw new SofaScoreFetchException("SofaScore tournament catalogue request failed");
        }
        return tournamentsResponse.getUniqueTournaments() != null
                ? tournamentsResponse.getUniqueTournaments() : Collections.emptyList();
    }

    @Override
    public List<SofaSeasonData.SeasonDTO> fetchSeasonByTournamentId(Long sofaTournamentId, Page page) {
        String url = String.format(FETCH_SEASON_BY_TOURNAMENT_URI, appProperties.getSofaScore().getApi(), sofaTournamentId);
        SofaSeasonData seasonData = safeFetch(url, page, SofaSeasonData.class);
        if (seasonData == null) {
            throw new SofaScoreFetchException("SofaScore season request failed for tournament " + sofaTournamentId);
        }
        return seasonData.getSeasons() != null ? seasonData.getSeasons() : Collections.emptyList();
    }

    @Override
    public List<SofaStandingsData.StandingGroupDTO> fetchStandingTotal(Long sofaTournamentId, Long sofaSeasonId, Page page) {
        String url = String.format(FETCH_STANDINGS_TOTAL_BY_TOURNAMENT_AND_SEASON_URI,
                appProperties.getSofaScore().getApi(), sofaTournamentId, sofaSeasonId);
        SofaStandingsData sofaStandingsData = safeFetch(url, page, SofaStandingsData.class);
        if (sofaStandingsData == null) {
            throw new SofaScoreFetchException("SofaScore standings request failed for tournament "
                    + sofaTournamentId + " season " + sofaSeasonId);
        }
        return sofaStandingsData.getStandings() != null
                ? sofaStandingsData.getStandings() : Collections.emptyList();
    }

    @Override
    public List<SofaMatchesData.SofaEventDTO> fetchMatchesByRound(Long sofaTournamentId, Long sofaSeasonId, int round, Page page) {
        String url = String.format(FETCH_MATCH_BY_ROUND_URI, appProperties.getSofaScore().getApi(),
                sofaTournamentId, sofaSeasonId, round);
        SofaMatchesData matchData = safeFetch(url, page, SofaMatchesData.class);
        if (matchData == null) {
            throw new SofaScoreFetchException("SofaScore fixture request failed for tournament "
                    + sofaTournamentId + " season " + sofaSeasonId + " round " + round);
        }
        return matchData.getEvents() != null ? matchData.getEvents() : Collections.emptyList();
    }

    @Override
    public List<SofaMatchStatisticsData.PeriodStatisticsDTO> fetchMatchStatistics(Long sofaMatchId, Page page) {
        String url = String.format(FETCH_MATCH_STATISTICS_URI, appProperties.getSofaScore().getApi(), sofaMatchId);
        SofaMatchStatisticsData matchData = safeFetch(url, page, SofaMatchStatisticsData.class);
        if (matchData == null) {
            throw new SofaScoreFetchException("SofaScore statistics request failed for event " + sofaMatchId);
        }
        return matchData.getStatistics() != null ? matchData.getStatistics() : Collections.emptyList();
    }

    @Override
    public Map<Long, SofaOddsData.MatchOddDetailDTO> fetchDailyMatchOdds(Page page) {
        String endpoint = String.format(FETCH_DAILY_ALL_MATCH_ODDS_URI, DateTimeUtils.today());
        DynamicFetchResult result = safeFetch(
                appProperties.getSofaScore().getUrl(),
                Map.of(endpoint, SofaOddsData.class),
                page,
                this::triggerOdds);
        if (!result.hasData(endpoint)) return Collections.emptyMap();
        SofaOddsData oddsData = result.get(endpoint, SofaOddsData.class);
        return oddsData != null && oddsData.getOdds() != null
                ? oddsData.getOdds() : Collections.emptyMap();
    }

    private void triggerOdds(Page page) {
        try {
            Locator oddsText = page.locator("span:has-text('Odds'), div:has-text('Odds')").first();
            oddsText.waitFor(new Locator.WaitForOptions()
                    .setState(com.microsoft.playwright.options.WaitForSelectorState.ATTACHED)
                    .setTimeout(15000));
            page.evaluate("""
                    () => {
                        const input = document.querySelector("input[role='switch']");
                        if (input) {
                            const target = input.nextElementSibling || input.closest('label') || input;
                            target.click();
                        }
                    }
                    """);
        } catch (TimeoutError ex) {
            log.warn("SofaScore odds toggle was not found before timeout.");
        } catch (Exception ex) {
            log.warn("Could not activate SofaScore odds view: {}", ex.getMessage());
        }
    }

    @Override
    public List<SofaMatchesData.SofaEventDTO> fetchMatchesDailyByTournamentId(Long sofaTournamentId, Page page) {
        String endpoint = String.format(FETCH_MATCH_UPCOMING_BY_TEAM_AND_DATE_URI,
                sofaTournamentId, DateTimeUtils.today());
        DynamicFetchResult result = safeFetch(
                appProperties.getSofaScore().getUrl(),
                Map.of(endpoint, SofaMatchesData.class),
                page,
                null);
        if (!result.hasData(endpoint)) return Collections.emptyList();
        SofaMatchesData matchData = result.get(endpoint, SofaMatchesData.class);
        return matchData != null && matchData.getEvents() != null
                ? matchData.getEvents() : Collections.emptyList();
    }

    @Override
    public List<SofaMatchesData.SofaEventDTO> fetchHistoriesMatchesByTeamIdAndIndex(Team team, int index, Page page) {
        if (team == null || team.getId() == null) {
            return Collections.emptyList();
        }
        TeamExternalId teamExternalId = teamExternalIdRepository
                .findByTeamIdAndProvider(team.getId(), Provider.SOFA_SCORE)
                .orElse(null);
        if (teamExternalId == null) {
            log.warn("Skipping SofaScore team history: no provider ID mapping for teamId={}", team.getId());
            return Collections.emptyList();
        }
        String sofaTeamId = teamExternalId.getExternalId();
        String loadPageUrl = String.format(FETCH_MATCH_HISTORIES_BY_TEAM_ID_AND_INDEX_PAGE, appProperties.getSofaScore().getUrl(),
                team.getSlug(), sofaTeamId);
        String historiesEndpoint = String.format(FETCH_MATCH_HISTORIES_BY_TEAM_ID_AND_INDEX_URI, sofaTeamId, index);
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
