package com.mmo.module.fb.ingest.adapter.out.provider.sofascore.client;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.mmo.AppProperties;
import com.mmo.core.utils.DateTimeUtils;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaDailyMatchWrapper;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreLineupsResponseDTO;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.EventDTO;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.MatchOddDetailDTO;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.MatchesResponseDTO;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.OddsResponseDTO;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.PeriodStatisticsDTO;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.SeasonDTO;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.SeasonsResponseDTO;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.StandingGroupDTO;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.StandingsResponseDTO;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.StatisticsResponseDTO;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.TournamentsResponseDTO;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.UniqueTournamentDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class SofaScoreClientImpl extends AbstractSofaScoreClient implements SofaScoreClient {
    private static final long CAPTCHA_WAIT_DEFAULT_SECONDS = 180L;

    private final AppProperties appProperties;

    private String webUrl() {
        return sofaScoreConfig().getWebUrl().replaceAll("/+$", "");
    }

    private AppProperties.ProviderConfig sofaScoreConfig() {
        AppProperties.ProviderConfig config = appProperties.getIngestion().getProviderConfig()
                .get(Provider.SOFA_SCORE);
        if (config == null || !config.isEnabled()) {
            throw new IllegalStateException("SofaScore provider is disabled or not configured");
        }
        return config;
    }

    @Override
    protected boolean waitForManualCaptchaResolution(Page page, String requestedUrl) {
        AppProperties.ProviderConfig config = sofaScoreConfig();
        if (!Boolean.parseBoolean(config.getOption("manual-captcha-intervention"))) {
            return false;
        }

        long timeoutSeconds = CAPTCHA_WAIT_DEFAULT_SECONDS;
        String configuredTimeout = config.getOption("captcha-wait-timeout-seconds");
        if (configuredTimeout != null && !configuredTimeout.isBlank()) {
            try {
                timeoutSeconds = Math.max(1L, Long.parseLong(configuredTimeout));
            } catch (NumberFormatException exception) {
                log.warn("Invalid SofaScore captcha-wait-timeout-seconds; using default {} seconds",
                        CAPTCHA_WAIT_DEFAULT_SECONDS);
            }
        }

        log.warn("Manual SofaScore CAPTCHA intervention required. Open browser page to solve it manually: {}",
                page.url());
        try {
            page.bringToFront();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
            while (System.nanoTime() < deadline && !page.isClosed()) {
                String currentUrl = page.url();
                if (currentUrl == null
                        || !currentUrl.toLowerCase(java.util.Locale.ROOT).contains("/captcha.html")) {
                    return true;
                }
                page.waitForTimeout(1000);
            }
        } catch (Exception exception) {
            log.warn("Manual CAPTCHA wait failed for requested URL {}: {}",
                    requestedUrl, exception.getMessage());
            return false;
        }

        log.warn("Manual SofaScore CAPTCHA intervention timed out after {} seconds. currentUrl={}",
                timeoutSeconds, page.isClosed() ? "<page-closed>" : page.url());
        return false;
    }

    @Override
    public List<UniqueTournamentDTO> fetchLeagues(Page page) {
        SofaScorePageSpec pageSpec = footballCataloguePage();
        TournamentsResponseDTO response = fetchRequired(pageSpec, page, TournamentsResponseDTO.class);
        return response.uniqueTournaments() == null ? Collections.emptyList() : response.uniqueTournaments();
    }

    @Override
    public List<SeasonDTO> fetchSeasonByTournamentId(Long tournamentId, Page page) {
        requirePositiveId(tournamentId, "tournamentId");
        SofaScorePageSpec pageSpec = SofaScorePageSpecs.seasons(webUrl(), tournamentId);
        SeasonsResponseDTO response = fetchRequired(pageSpec, page, SeasonsResponseDTO.class);
        return response.seasons() == null ? Collections.emptyList() : response.seasons();
    }

    @Override
    public List<StandingGroupDTO> fetchStandingTotal(Long tournamentId, Long seasonId, Page page) {
        requirePositiveId(tournamentId, "tournamentId");
        requirePositiveId(seasonId, "seasonId");
        SofaScorePageSpec pageSpec = SofaScorePageSpecs.standings(webUrl(), tournamentId, seasonId);
        StandingsResponseDTO response = fetchRequired(pageSpec, page, StandingsResponseDTO.class);
        return response.standings() == null ? Collections.emptyList() : response.standings();
    }

    @Override
    public List<EventDTO> fetchMatchesByRound(
            Long tournamentId, Long seasonId, int round, Page page) {
        requirePositiveId(tournamentId, "tournamentId");
        requirePositiveId(seasonId, "seasonId");
        if (round <= 0) {
            throw new IllegalArgumentException("SofaScore round must be positive");
        }
        SofaScorePageSpec pageSpec = SofaScorePageSpecs.roundEvents(webUrl(), tournamentId, seasonId, round);
        MatchesResponseDTO response = fetchRequired(pageSpec, page, MatchesResponseDTO.class);
        return response.events() == null ? Collections.emptyList() : response.events();
    }

    @Override
    public MatchesResponseDTO fetchSeasonMatchesPage(
            Long tournamentId, Long seasonId, String direction, int pageIndex, Page page) {
        requirePositiveId(tournamentId, "tournamentId");
        requirePositiveId(seasonId, "seasonId");
        if (!"last".equals(direction) && !"next".equals(direction)) {
            throw new IllegalArgumentException("SofaScore event direction must be 'last' or 'next'");
        }
        if (pageIndex < 0) {
            throw new IllegalArgumentException("SofaScore event page index must not be negative");
        }
        SofaScorePageSpec pageSpec =
                SofaScorePageSpecs.seasonEvents(webUrl(), tournamentId, seasonId, direction, pageIndex);
        return fetchRequired(pageSpec, page, MatchesResponseDTO.class);
    }

    @Override
    public List<PeriodStatisticsDTO> fetchMatchStatistics(Long matchId, Page page) {
        requirePositiveId(matchId, "matchId");
        SofaScorePageSpec pageSpec = SofaScorePageSpecs.matchStatistics(webUrl(), matchId);
        StatisticsResponseDTO response = fetchRequired(pageSpec, page, StatisticsResponseDTO.class);
        return response.statistics() == null ? Collections.emptyList() : response.statistics();
    }

    @Override
    public Map<Long, MatchOddDetailDTO> fetchDailyMatchOdds(Page page) {
        SofaScorePageSpec pageSpec = footballCatalogueWithOddsPage();
        DynamicFetchResult result = safeFetch(pageSpec, page, this::activateOddsView);
        String oddsEndpoint = SofaScorePageSpecs.dailyOddsEndpoint(DateTimeUtils.today());
        OddsResponseDTO response = result.get(oddsEndpoint, OddsResponseDTO.class);
        return response == null || response.odds() == null ? Collections.emptyMap() : response.odds();
    }

    @Override
    public List<EventDTO> fetchMatchesDailyByTournamentId(Long tournamentId, Page page) {
        requirePositiveId(tournamentId, "tournamentId");
        SofaScorePageSpec pageSpec =
                SofaScorePageSpecs.dailyTournamentEvents(webUrl(), tournamentId, DateTimeUtils.today());
        MatchesResponseDTO response = fetchRequired(pageSpec, page, MatchesResponseDTO.class);
        return response.events() == null ? Collections.emptyList() : response.events();
    }

    @Override
    public List<EventDTO> fetchHistoriesMatchesByTeamIdAndIndex(
            String teamId, String teamSlug, int pageIndex, Page page) {
        if (teamId == null || teamId.isBlank() || teamSlug == null || teamSlug.isBlank()) {
            return Collections.emptyList();
        }
        if (pageIndex < 0) {
            throw new IllegalArgumentException("SofaScore team-history page index must not be negative");
        }
        SofaScorePageSpec pageSpec = SofaScorePageSpecs.teamHistory(webUrl(), teamSlug, teamId, pageIndex);
        MatchesResponseDTO response = fetchRequired(pageSpec, page, MatchesResponseDTO.class);
        return response.events() == null ? Collections.emptyList() : response.events();
    }

    @Override
    public EventDTO fetchMatchById(Long matchId, Page page) {
        requirePositiveId(matchId, "matchId");
        SofaScorePageSpec pageSpec = SofaScorePageSpecs.matchDetails(webUrl(), matchId);
        var response = fetchRequired(pageSpec, page,
                com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.MatchResponseDTO.class);
        if (response.event() == null) {
            throw new SofaScoreFetchException("SofaScore match endpoint returned no event for match " + matchId);
        }
        return response.event();
    }

    @Override
    public SofaDailyMatchWrapper fetchMatchesDaily(Long tournamentId, Page page) {
        requirePositiveId(tournamentId, "tournamentId");
        SofaScorePageSpec pageSpec =
                SofaScorePageSpecs.dailyEventsWithOdds(webUrl(), tournamentId, DateTimeUtils.today());
        DynamicFetchResult result = safeFetch(pageSpec, page, this::activateOddsView);

        MatchesResponseDTO matches = result.get(
                pageSpec.singleRequiredEndpoint().endpoint(), MatchesResponseDTO.class);
        if (matches == null) {
            throw new SofaScoreFetchException("SofaScore daily-match response was not captured for tournament "
                    + tournamentId);
        }
        String oddsEndpoint = SofaScorePageSpecs.dailyOddsEndpoint(DateTimeUtils.today());
        OddsResponseDTO odds = result.get(oddsEndpoint, OddsResponseDTO.class);
        return new SofaDailyMatchWrapper(
                matches.events() == null ? Collections.emptyList() : matches.events(),
                odds == null || odds.odds() == null ? Collections.emptyMap() : odds.odds());
    }

    @Override
    public SofaScoreLineupsResponseDTO fetchLineups(Long matchId, Page page) {
        requirePositiveId(matchId, "matchId");
        SofaScorePageSpec pageSpec = SofaScorePageSpecs.lineups(webUrl(), matchId);
        DynamicFetchResult result = safeFetch(pageSpec, page, this::activateLineupsView);
        return result.get(SofaScorePageSpecs.lineupsEndpoint(matchId), SofaScoreLineupsResponseDTO.class);
    }

    private SofaScorePageSpec footballCataloguePage() {
        AppProperties.ProviderConfig config = sofaScoreConfig();
        return SofaScorePageSpecs.footballCatalogue(
                webUrl(), config.getOption("tournament-region"));
    }

    private SofaScorePageSpec footballCatalogueWithOddsPage() {
        AppProperties.ProviderConfig config = sofaScoreConfig();
        return SofaScorePageSpecs.footballCatalogueWithOdds(
                webUrl(), config.getOption("tournament-region"), DateTimeUtils.today());
    }

    private void activateLineupsView(Page page) {
        try {
            Locator lineupsTab = page.getByText("Lineups", new Page.GetByTextOptions().setExact(true)).first();
            lineupsTab.waitFor(new Locator.WaitForOptions()
                    .setState(com.microsoft.playwright.options.WaitForSelectorState.ATTACHED)
                    .setTimeout(10_000));
            lineupsTab.click();
        } catch (TimeoutError exception) {
            log.debug("SofaScore lineups tab was not available on the loaded page.");
        } catch (Exception exception) {
            log.warn("Could not activate SofaScore lineups view: {}", exception.getMessage());
        }
    }

    private void activateOddsView(Page page) {
        try {
            Locator oddsLabel = page.locator("span:has-text('Odds'), div:has-text('Odds')").first();
            oddsLabel.waitFor(new Locator.WaitForOptions()
                    .setState(com.microsoft.playwright.options.WaitForSelectorState.ATTACHED)
                    .setTimeout(15_000));
            page.evaluate("""
                    () => {
                        const input = document.querySelector("input[role='switch']");
                        if (!input) return false;
                        const target = input.nextElementSibling || input.closest('label') || input;
                        target.click();
                        return true;
                    }
                    """);
        } catch (TimeoutError exception) {
            log.debug("SofaScore odds control was not available on the loaded page.");
        } catch (Exception exception) {
            log.warn("Could not activate SofaScore odds view: {}", exception.getMessage());
        }
    }

    private <T> T fetchRequired(SofaScorePageSpec pageSpec, Page page, Class<T> responseType) {
        SofaScoreEndpointSpec requiredEndpoint = pageSpec.singleRequiredEndpoint();
        if (!responseType.isAssignableFrom(requiredEndpoint.responseType())) {
            throw new IllegalArgumentException("Response type does not match the required endpoint specification");
        }
        DynamicFetchResult result = safeFetch(pageSpec, page, null);
        T response = result.get(requiredEndpoint.endpoint(), responseType);
        if (response == null) {
            throw new SofaScoreFetchException("Required SofaScore response was not captured: "
                    + requiredEndpoint.endpoint());
        }
        return response;
    }

    private void requirePositiveId(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("SofaScore " + fieldName + " must be positive");
        }
    }
}
