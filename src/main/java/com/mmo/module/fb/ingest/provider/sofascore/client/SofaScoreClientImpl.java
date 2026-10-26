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

    private static final String FETCH_ALL_UNIQUE_TOURNAMENTS_URI = "config/default-unique-tournaments/%s/football";
    private static final String FETCH_SEASON_BY_TOURNAMENT_URI = "%sunique-tournament/%d/seasons";
    private static final String FETCH_STANDINGS_TOTAL_BY_TOURNAMENT_AND_SEASON_URI = "%sunique-tournament/%d/season/%d/standings/total";
    private static final String FETCH_MATCH_BY_ROUND_URI = "%sunique-tournament/%d/season/%d/events/round/%d";
    private static final String FETCH_SEASON_MATCHES_PAGE_URI = "%sunique-tournament/%d/season/%d/events/%s/%d";
    private static final String FETCH_MATCH_STATISTICS_URI = "%sevent/%d/statistics";
    private static final String FETCH_DAILY_ALL_MATCH_ODDS_URI = "/api/v1/sport/football/odds/1/%s";
    private static final String FETCH_MATCH_UPCOMING_BY_TEAM_AND_DATE_URI = "/api/v1/unique-tournament/%d/scheduled-events/%s";
    private static final String FETCH_MATCH_HISTORIES_BY_TEAM_ID_AND_INDEX_URI = "team/%s/events/last/%d";
    private static final String FETCH_MATCH_HISTORIES_BY_TEAM_ID_AND_INDEX_PAGE = "%s/football/team/%s/%s";
    private static final String FETCH_MATCH_BY_ID_URI = "%sevent/%d";

    @Override
    public List<UniqueTournamentDTO> fetchLeagues(Page page) {
        String region = appProperties.getSofaScore().getTournamentRegion();
        if (region == null || !region.matches("[A-Za-z]{2}")) region = "EN";
        String url = appProperties.getSofaScore().getApi()
                .concat(String.format(FETCH_ALL_UNIQUE_TOURNAMENTS_URI, region.toUpperCase(java.util.Locale.ROOT)));
        TournamentsResponseDTO response = safeFetch(url, page, TournamentsResponseDTO.class);
        if (response == null) throw new SofaScoreFetchException("SofaScore tournament catalogue request failed");
        return response.uniqueTournaments() != null ? response.uniqueTournaments() : Collections.emptyList();
    }

    @Override
    public List<SeasonDTO> fetchSeasonByTournamentId(Long tournamentId, Page page) {
        String url = String.format(FETCH_SEASON_BY_TOURNAMENT_URI, appProperties.getSofaScore().getApi(), tournamentId);
        SeasonsResponseDTO response = safeFetch(url, page, SeasonsResponseDTO.class);
        if (response == null) throw new SofaScoreFetchException("SofaScore season request failed for tournament " + tournamentId);
        return response.seasons() != null ? response.seasons() : Collections.emptyList();
    }

    @Override
    public List<StandingGroupDTO> fetchStandingTotal(Long tournamentId, Long seasonId, Page page) {
        String url = String.format(FETCH_STANDINGS_TOTAL_BY_TOURNAMENT_AND_SEASON_URI,
                appProperties.getSofaScore().getApi(), tournamentId, seasonId);
        StandingsResponseDTO response = safeFetch(url, page, StandingsResponseDTO.class);
        if (response == null) throw new SofaScoreFetchException(
                "SofaScore standings request failed for tournament " + tournamentId + " season " + seasonId);
        return response.standings() != null ? response.standings() : Collections.emptyList();
    }

    @Override
    public List<EventDTO> fetchMatchesByRound(Long tournamentId, Long seasonId, int round, Page page) {
        String url = String.format(FETCH_MATCH_BY_ROUND_URI, appProperties.getSofaScore().getApi(),
                tournamentId, seasonId, round);
        MatchesResponseDTO response = safeFetch(url, page, MatchesResponseDTO.class);
        if (response == null) {
            throw new SofaScoreFetchException("SofaScore fixture request failed for tournament "
                    + tournamentId + " season " + seasonId + " round " + round);
        }
        return response.events() != null ? response.events() : Collections.emptyList();
    }

    @Override
    public MatchesResponseDTO fetchSeasonMatchesPage(
            Long tournamentId, Long seasonId, String direction, int pageIndex, Page page) {
        if (!"last".equals(direction) && !"next".equals(direction)) {
            throw new IllegalArgumentException("SofaScore event direction must be 'last' or 'next'");
        }
        String url = String.format(FETCH_SEASON_MATCHES_PAGE_URI, appProperties.getSofaScore().getApi(),
                tournamentId, seasonId, direction, pageIndex);
        MatchesResponseDTO response = safeFetch(url, page, MatchesResponseDTO.class);
        // Some competitions have no events in one direction; the direct client treats HTTP 404 as an empty page.
        return response == null ? new MatchesResponseDTO(Collections.emptyList(), false) : response;
    }

    @Override
    public List<PeriodStatisticsDTO> fetchMatchStatistics(Long eventId, Page page) {
        String url = String.format(FETCH_MATCH_STATISTICS_URI, appProperties.getSofaScore().getApi(), eventId);
        StatisticsResponseDTO response = safeFetch(url, page, StatisticsResponseDTO.class);
        if (response == null) throw new SofaScoreFetchException("SofaScore statistics request failed for event " + eventId);
        return response.statistics() != null ? response.statistics() : Collections.emptyList();
    }

    @Override
    public Map<Long, MatchOddDetailDTO> fetchDailyMatchOdds(Page page) {
        String endpoint = String.format(FETCH_DAILY_ALL_MATCH_ODDS_URI, DateTimeUtils.today());
        DynamicFetchResult result = safeFetch(appProperties.getSofaScore().getUrl(),
                Map.of(endpoint, OddsResponseDTO.class), page, this::triggerOdds);
        if (!result.hasData(endpoint)) return Collections.emptyMap();
        OddsResponseDTO response = result.get(endpoint, OddsResponseDTO.class);
        return response != null && response.odds() != null ? response.odds() : Collections.emptyMap();
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
        } catch (TimeoutError exception) {
            log.warn("SofaScore odds toggle was not found before timeout");
        } catch (Exception exception) {
            log.warn("Could not activate SofaScore odds view: {}", exception.getMessage());
        }
    }

    @Override
    public List<EventDTO> fetchMatchesDailyByTournamentId(Long tournamentId, Page page) {
        String endpoint = String.format(FETCH_MATCH_UPCOMING_BY_TEAM_AND_DATE_URI, tournamentId, DateTimeUtils.today());
        DynamicFetchResult result = safeFetch(appProperties.getSofaScore().getUrl(),
                Map.of(endpoint, MatchesResponseDTO.class), page, null);
        if (!result.hasData(endpoint)) return Collections.emptyList();
        MatchesResponseDTO response = result.get(endpoint, MatchesResponseDTO.class);
        return response != null && response.events() != null ? response.events() : Collections.emptyList();
    }

    @Override
    public List<EventDTO> fetchHistoriesMatchesByTeamIdAndIndex(Team team, int index, Page page) {
        if (team == null || team.getId() == null) return Collections.emptyList();
        TeamExternalId mapping = teamExternalIdRepository
                .findByTeamIdAndProvider(team.getId(), Provider.SOFA_SCORE).orElse(null);
        if (mapping == null) {
            log.warn("Skipping SofaScore team history: no provider ID mapping for teamId={}", team.getId());
            return Collections.emptyList();
        }
        String teamId = mapping.getExternalId();
        String pageUrl = String.format(FETCH_MATCH_HISTORIES_BY_TEAM_ID_AND_INDEX_PAGE,
                appProperties.getSofaScore().getUrl(), team.getSlug(), teamId);
        String endpoint = String.format(FETCH_MATCH_HISTORIES_BY_TEAM_ID_AND_INDEX_URI, teamId, index);
        DynamicFetchResult result = safeFetch(pageUrl, Map.of(endpoint, MatchesResponseDTO.class), page, null);
        if (!result.hasData(endpoint)) return Collections.emptyList();
        MatchesResponseDTO response = result.get(endpoint, MatchesResponseDTO.class);
        return response != null && response.events() != null ? response.events() : Collections.emptyList();
    }

    @Override
    public EventDTO fetchMatchById(Long eventId, Page page) {
        String url = String.format(FETCH_MATCH_BY_ID_URI, appProperties.getSofaScore().getApi(), eventId);
        MatchResponseDTO response = safeFetch(url, page, MatchResponseDTO.class);
        return response != null ? response.event() : null;
    }

    @Override
    public SofaDailyMatchWrapper fetchMatchesDaily(Long tournamentId, Page page) {
        String pageUrl = appProperties.getSofaScore().getUrl();
        String matchesEndpoint = String.format(FETCH_MATCH_UPCOMING_BY_TEAM_AND_DATE_URI, tournamentId, DateTimeUtils.today());
        String oddsEndpoint = String.format(FETCH_DAILY_ALL_MATCH_ODDS_URI, DateTimeUtils.today());
        Map<String, Class<?>> config = Map.of(matchesEndpoint, MatchesResponseDTO.class, oddsEndpoint, OddsResponseDTO.class);
        DynamicFetchResult result = safeFetch(pageUrl, config, page, this::triggerOdds);
        List<EventDTO> events = new ArrayList<>();
        Map<Long, MatchOddDetailDTO> odds = new HashMap<>();
        if (result.hasData(matchesEndpoint)) {
            MatchesResponseDTO response = result.get(matchesEndpoint, MatchesResponseDTO.class);
            if (response != null && response.events() != null) events = response.events();
        }
        if (result.hasData(oddsEndpoint)) {
            OddsResponseDTO response = result.get(oddsEndpoint, OddsResponseDTO.class);
            if (response != null && response.odds() != null) odds = response.odds();
        }
        return new SofaDailyMatchWrapper(events, odds);
    }
}
