package com.mmo.module.fb.ingest.provider.sofascore.client;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.mmo.AppProperties;
import com.mmo.core.utils.DateTimeUtils;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.model.DynamicFetchResult;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaDailyMatchWrapper;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaScoreResponseDTO.EventDTO;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaScoreResponseDTO.MatchOddDetailDTO;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaScoreResponseDTO.MatchResponseDTO;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaScoreResponseDTO.MatchesResponseDTO;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaScoreResponseDTO.OddsResponseDTO;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaScoreResponseDTO.PeriodStatisticsDTO;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaScoreResponseDTO.SeasonDTO;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaScoreResponseDTO.SeasonsResponseDTO;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaScoreResponseDTO.StandingGroupDTO;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaScoreResponseDTO.StandingsResponseDTO;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaScoreResponseDTO.StatisticsResponseDTO;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaScoreResponseDTO.TournamentsResponseDTO;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaScoreResponseDTO.UniqueTournamentDTO;
import com.mmo.module.fb.team.entity.Team;
import com.mmo.module.fb.team.entity.TeamExternalId;
import com.mmo.module.fb.team.repository.TeamExternalIdRepository;
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

    // Page URLs are used to trigger the browser requests; URI constants only match API responses.
    private static final String FETCH_ALL_UNIQUE_TOURNAMENTS_PAGE = "%s/football";
    private static final String FETCH_SEASON_BY_TOURNAMENT_PAGE = "%s/football/unique-tournament/%d";
    private static final String FETCH_STANDINGS_TOTAL_BY_TOURNAMENT_AND_SEASON_PAGE = "%s/football/unique-tournament/%d/season/%d/standings";
    private static final String FETCH_MATCH_BY_ROUND_PAGE = "%s/football/unique-tournament/%d/season/%d";
    private static final String FETCH_SEASON_MATCHES_PAGE = "%s/football/unique-tournament/%d/season/%d";
    private static final String FETCH_MATCH_STATISTICS_PAGE = "%s/football/match/%d";
    private static final String FETCH_MATCH_UPCOMING_BY_TEAM_AND_DATE_PAGE = "%s/football/unique-tournament/%d";
    private static final String FETCH_MATCH_BY_ID_PAGE = "%s/football/match/%d";
    private static final String FETCH_ALL_UNIQUE_TOURNAMENTS_URI = "config/default-unique-tournaments/%s/football";
    private static final String FETCH_SEASON_BY_TOURNAMENT_URI = "unique-tournament/%d/seasons";
    private static final String FETCH_STANDINGS_TOTAL_BY_TOURNAMENT_AND_SEASON_URI = "unique-tournament/%d/season/%d/standings/total";
    private static final String FETCH_MATCH_BY_ROUND_URI = "unique-tournament/%d/season/%d/events/round/%d";
    private static final String FETCH_SEASON_MATCHES_PAGE_URI = "unique-tournament/%d/season/%d/events/%s/%d";
    private static final String FETCH_MATCH_STATISTICS_URI = "event/%d/statistics";
    private static final String FETCH_DAILY_ALL_MATCH_ODDS_URI = "/api/v1/sport/football/odds/1/%s";
    private static final String FETCH_MATCH_UPCOMING_BY_TEAM_AND_DATE_URI = "/api/v1/unique-tournament/%d/scheduled-events/%s";
    private static final String FETCH_MATCH_HISTORIES_BY_TEAM_ID_AND_INDEX_URI = "team/%s/events/last/%d";
    private static final String FETCH_MATCH_HISTORIES_BY_TEAM_ID_AND_INDEX_PAGE = "%s/football/team/%s/%s";
    private static final String FETCH_MATCH_BY_ID_URI = "event/%d";

    private String webUrl() {
        return sofaScoreConfig().getWebUrl().replaceAll("/+$", "");
    }

    private AppProperties.ProviderConfig sofaScoreConfig() {
        AppProperties.ProviderConfig config = appProperties.getIngestion().getProviderConfig().get(Provider.SOFA_SCORE);
        if (config == null || !config.isEnabled()) throw new IllegalStateException("SofaScore provider is disabled or not configured");
        return config;
    }

    @Override
    public List<UniqueTournamentDTO> fetchLeagues(Page page) {
        String region = sofaScoreConfig().getOption("tournament-region");
        if (region == null || !region.matches("[A-Za-z]{2}")) {
            region = "EN";
        }
        String loadPageUrl = String.format(FETCH_ALL_UNIQUE_TOURNAMENTS_PAGE, webUrl());
        String endpoint = String.format(FETCH_ALL_UNIQUE_TOURNAMENTS_URI, region.toUpperCase(java.util.Locale.ROOT));
        TournamentsResponseDTO tournamentsResponse = fetchFromWebPage(loadPageUrl, endpoint, page, TournamentsResponseDTO.class);
        if (tournamentsResponse == null) {
            throw new SofaScoreFetchException("SofaScore tournament catalogue request failed");
        }
        return tournamentsResponse.uniqueTournaments() != null
                ? tournamentsResponse.uniqueTournaments() : Collections.emptyList();
    }

    @Override
    public List<SeasonDTO> fetchSeasonByTournamentId(Long sofaTournamentId, Page page) {
        String loadPageUrl = String.format(FETCH_SEASON_BY_TOURNAMENT_PAGE, webUrl(), sofaTournamentId);
        String endpoint = String.format(FETCH_SEASON_BY_TOURNAMENT_URI, sofaTournamentId);
        SeasonsResponseDTO seasonData = fetchFromWebPage(loadPageUrl, endpoint, page, SeasonsResponseDTO.class);
        if (seasonData == null) {
            throw new SofaScoreFetchException("SofaScore season request failed for tournament " + sofaTournamentId);
        }
        return seasonData.seasons() != null ? seasonData.seasons() : Collections.emptyList();
    }

    @Override
    public List<StandingGroupDTO> fetchStandingTotal(Long sofaTournamentId, Long sofaSeasonId, Page page) {
        String loadPageUrl = String.format(FETCH_STANDINGS_TOTAL_BY_TOURNAMENT_AND_SEASON_PAGE,
                webUrl(), sofaTournamentId, sofaSeasonId);
        String endpoint = String.format(FETCH_STANDINGS_TOTAL_BY_TOURNAMENT_AND_SEASON_URI,
                sofaTournamentId, sofaSeasonId);
        StandingsResponseDTO sofaStandingsData = fetchFromWebPage(loadPageUrl, endpoint, page, StandingsResponseDTO.class);
        if (sofaStandingsData == null) {
            throw new SofaScoreFetchException("SofaScore standings request failed for tournament "
                                              + sofaTournamentId + " season " + sofaSeasonId);
        }
        return sofaStandingsData.standings() != null
                ? sofaStandingsData.standings() : Collections.emptyList();
    }

    @Override
    public List<EventDTO> fetchMatchesByRound(Long sofaTournamentId, Long sofaSeasonId, int round, Page page) {
        String loadPageUrl = String.format(FETCH_MATCH_BY_ROUND_PAGE, webUrl(), sofaTournamentId, sofaSeasonId);
        String endpoint = String.format(FETCH_MATCH_BY_ROUND_URI, sofaTournamentId, sofaSeasonId, round);
        MatchesResponseDTO matchData = fetchFromWebPage(loadPageUrl, endpoint, page, MatchesResponseDTO.class);
        // A missing round is a valid empty result; access/rate-limit errors are thrown by safeFetch.
        if (matchData == null) return Collections.emptyList();
        return matchData.events() != null ? matchData.events() : Collections.emptyList();
    }

    @Override
    public MatchesResponseDTO fetchSeasonMatchesPage(
            Long tournamentId, Long seasonId, String direction, int pageIndex, Page page) {
        if (!"last".equals(direction) && !"next".equals(direction)) {
            throw new IllegalArgumentException("SofaScore event direction must be 'last' or 'next'");
        }
        String loadPageUrl = String.format(FETCH_SEASON_MATCHES_PAGE, webUrl(), tournamentId, seasonId);
        String endpoint = String.format(FETCH_SEASON_MATCHES_PAGE_URI,
                tournamentId, seasonId, direction, pageIndex);
        // A missing response preserves the existing empty-page contract.
        MatchesResponseDTO response = fetchFromWebPage(loadPageUrl, endpoint, page, MatchesResponseDTO.class);
        return response == null ? new MatchesResponseDTO(Collections.emptyList(), false) : response;
    }

    @Override
    public List<PeriodStatisticsDTO> fetchMatchStatistics(Long sofaMatchId, Page page) {
        String loadPageUrl = String.format(FETCH_MATCH_STATISTICS_PAGE, webUrl(), sofaMatchId);
        String endpoint = String.format(FETCH_MATCH_STATISTICS_URI, sofaMatchId);
        StatisticsResponseDTO matchData = fetchFromWebPage(loadPageUrl, endpoint, page, StatisticsResponseDTO.class);
        if (matchData == null) {
            throw new SofaScoreFetchException("SofaScore statistics request failed for event " + sofaMatchId);
        }
        return matchData.statistics() != null ? matchData.statistics() : Collections.emptyList();
    }

    @Override
    public Map<Long, MatchOddDetailDTO> fetchDailyMatchOdds(Page page) {
        String endpoint = String.format(FETCH_DAILY_ALL_MATCH_ODDS_URI, DateTimeUtils.today());
        DynamicFetchResult result = safeFetch(
                sofaScoreConfig().getWebUrl(),
                Map.of(endpoint, OddsResponseDTO.class),
                page,
                this::triggerOdds);
        if (!result.hasData(endpoint)) return Collections.emptyMap();
        OddsResponseDTO oddsData = result.get(endpoint, OddsResponseDTO.class);
        return oddsData != null && oddsData.odds() != null
                ? oddsData.odds() : Collections.emptyMap();
    }

    /**
     * Loads the configured SofaScore web page, captures its network responses, and parses
     * only the response whose URL contains the expected API endpoint fragment.
     */
    private <T> T fetchFromWebPage(String loadPageUrl, String endpoint, Page page, Class<T> responseType) {
        DynamicFetchResult result = safeFetch(
                loadPageUrl,
                Map.of(endpoint, responseType),
                page,
                null);
        if (!result.hasData(endpoint)) {
            log.warn("SofaScore page '{}' did not emit expected endpoint '{}'",
                    loadPageUrl, endpoint);
            return null;
        }
        return result.get(endpoint, responseType);
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
    public List<EventDTO> fetchMatchesDailyByTournamentId(Long sofaTournamentId, Page page) {
        String loadPageUrl = String.format(FETCH_MATCH_UPCOMING_BY_TEAM_AND_DATE_PAGE, webUrl(), sofaTournamentId);
        String endpoint = String.format(FETCH_MATCH_UPCOMING_BY_TEAM_AND_DATE_URI,
                sofaTournamentId, DateTimeUtils.today());
        MatchesResponseDTO matchData = fetchFromWebPage(loadPageUrl, endpoint, page, MatchesResponseDTO.class);
        return matchData != null && matchData.events() != null
                ? matchData.events() : Collections.emptyList();
    }

    @Override
    public List<EventDTO> fetchHistoriesMatchesByTeamIdAndIndex(Team team, int index, Page page) {
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
        String loadPageUrl = String.format(FETCH_MATCH_HISTORIES_BY_TEAM_ID_AND_INDEX_PAGE,
                webUrl(), team.getSlug(), sofaTeamId);
        String historiesEndpoint = String.format(FETCH_MATCH_HISTORIES_BY_TEAM_ID_AND_INDEX_URI, sofaTeamId, index);
        MatchesResponseDTO matchData = fetchFromWebPage(loadPageUrl, historiesEndpoint, page, MatchesResponseDTO.class);
        return matchData != null && matchData.events() != null
                ? matchData.events() : Collections.emptyList();
    }

    @Override
    public EventDTO fetchMatchById(Long sofaMatchId, Page page) {
        String loadPageUrl = String.format(FETCH_MATCH_BY_ID_PAGE, webUrl(), sofaMatchId);
        String endpoint = String.format(FETCH_MATCH_BY_ID_URI, sofaMatchId);
        MatchResponseDTO matchData = fetchFromWebPage(loadPageUrl, endpoint, page, MatchResponseDTO.class);
        return matchData != null ? matchData.event() : null;
    }

    @Override
    public SofaDailyMatchWrapper fetchMatchesDaily(Long sofaTournamentId, Page page) {
        String loadPageUrl = String.format(FETCH_MATCH_UPCOMING_BY_TEAM_AND_DATE_PAGE, webUrl(), sofaTournamentId);
        String matchEndpoint = String.format(FETCH_MATCH_UPCOMING_BY_TEAM_AND_DATE_URI, sofaTournamentId, DateTimeUtils.today());
        String oddsEndpoint = String.format(FETCH_DAILY_ALL_MATCH_ODDS_URI, DateTimeUtils.today());
        Map<String, Class<?>> configMap = Map.of(matchEndpoint, MatchesResponseDTO.class, oddsEndpoint, OddsResponseDTO.class);

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

        List<EventDTO> events = new ArrayList<>();
        Map<Long, MatchOddDetailDTO> oddsMap = new HashMap<>();

        if (result.hasData(matchEndpoint)) {
            MatchesResponseDTO matchData = result.get(matchEndpoint, MatchesResponseDTO.class);
            if (matchData != null) events = matchData.events();
        }

        if (result.hasData(oddsEndpoint)) {
            OddsResponseDTO oddsData = result.get(oddsEndpoint, OddsResponseDTO.class);
            if (oddsData != null) oddsMap = oddsData.odds();
        }
        return new SofaDailyMatchWrapper(events, oddsMap);
    }

}
