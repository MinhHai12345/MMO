package com.mmo.module.fb.ingest.adapter.out.provider.sofascore.client;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.TimeoutError;
import com.mmo.AppProperties;
import com.mmo.core.utils.DateTimeUtils;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaDailyMatchWrapper;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreLeagueCatalogueResult;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
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
        String configuredWebUrl = sofaScoreConfig().getWebUrl();
        if (configuredWebUrl == null || configuredWebUrl.isBlank()) {
            throw new IllegalStateException(
                    "SofaScore web URL is missing. Configure SOFA_SCORE_WEB_URL, for example https://www.sofascore.com");
        }
        String normalized = configuredWebUrl.trim().replaceAll("/+$", "");
        if (!normalized.startsWith("https://") && !normalized.startsWith("http://")) {
            throw new IllegalStateException("SofaScore web URL must use http:// or https://");
        }
        return normalized;
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
    public SofaScoreLeagueCatalogueResult fetchLeaguesWithSeasons(Page page, List<Long> selectedTournamentIds) {
        AppProperties.ProviderConfig config = sofaScoreConfig();
        SofaScoreLeaguePageSpec pageSpec = SofaScorePageSpecs.footballLeagueCatalogue(
                webUrl(), config.getOption("tournament-region"));

        AtomicReference<String> catalogueJson = new AtomicReference<>();
        AtomicReference<Integer> catalogueStatus = new AtomicReference<>();
        ConcurrentMap<Long, String> seasonJsonByTournamentId = new ConcurrentHashMap<>();
        ConcurrentMap<Long, Integer> seasonStatusByTournamentId = new ConcurrentHashMap<>();

        Consumer<Response> responseListener = response -> {
            String responseUrl = response.url();
            if (pageSpec.isCatalogueUrl(responseUrl)) {
                catalogueStatus.set(response.status());
                if (response.ok()) {
                    catalogueJson.compareAndSet(null, response.text());
                }
                return;
            }

            Long tournamentId = pageSpec.tournamentIdFromSeasonUrl(responseUrl);
            if (tournamentId == null) {
                return;
            }
            seasonStatusByTournamentId.put(tournamentId, response.status());
            if (response.ok()) {
                seasonJsonByTournamentId.putIfAbsent(tournamentId, response.text());
            }
        };

        page.onResponse(responseListener);
        try {
            log.info("[SOFA_SCORE][LEAGUE] Opening football page once: url={}, catalogueEndpoint={}",
                    pageSpec.loadPageUrl(), pageSpec.catalogueEndpoint());
            navigateWithRetry(pageSpec.loadPageUrl(), page);

            long catalogueDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (catalogueJson.get() == null && System.nanoTime() < catalogueDeadline) {
                page.waitForTimeout(100);
            }
            if (catalogueJson.get() == null) {
                throw new SofaScoreFetchException(
                        "SofaScore tournament catalogue was not captured. status=" + catalogueStatus.get()
                                + ", endpoint=" + pageSpec.catalogueEndpoint());
            }

            TournamentsResponseDTO catalogue = objectMapper.readValue(
                    catalogueJson.get(), TournamentsResponseDTO.class);
            List<UniqueTournamentDTO> tournaments = catalogue.uniqueTournaments() == null
                    ? List.of()
                    : catalogue.uniqueTournaments().stream()
                            .filter(java.util.Objects::nonNull)
                            .filter(tournament -> tournament.id() != null && tournament.id() > 0)
                            .toList();

            List<Long> tournamentIds = tournaments.stream()
                    .map(UniqueTournamentDTO::id)
                    .distinct()
                    .filter(id -> selectedTournamentIds == null || selectedTournamentIds.isEmpty()
                            || selectedTournamentIds.contains(id))
                    .toList();
            log.info("[SOFA_SCORE][LEAGUE] Catalogue captured: tournaments={}, ids={}",
                    tournaments.size(), tournamentIds);

            // The football page emits season requests itself. Give those responses a short
            // window to arrive before requesting only the IDs that the page did not emit.
            page.waitForTimeout(1500);
            List<Long> missingTournamentIds = tournamentIds.stream()
                    .filter(id -> !seasonJsonByTournamentId.containsKey(id))
                    .toList();

            if (!missingTournamentIds.isEmpty()) {
                log.info("[SOFA_SCORE][LEAGUE] Fetching missing season payloads in the same page context: ids={}",
                        missingTournamentIds);
                Object evaluated = page.evaluate("""
                        ids => Promise.all(ids.map(async id => {
                            const response = await fetch(
                                '/api/v1/unique-tournament/' + id + '/seasons',
                                { credentials: 'same-origin' }
                            );
                            return {
                                id: id,
                                status: response.status,
                                body: await response.text()
                            };
                        }))
                        """, missingTournamentIds);

                if (!(evaluated instanceof List<?> responses)) {
                    throw new SofaScoreFetchException(
                            "SofaScore season fetch did not return a response list for tournament IDs "
                                    + missingTournamentIds);
                }
                for (Object item : responses) {
                    if (!(item instanceof Map<?, ?> response)) {
                        continue;
                    }
                    Object idValue = response.get("id");
                    Object statusValue = response.get("status");
                    Object bodyValue = response.get("body");
                    if (!(idValue instanceof Number idNumber) || !(statusValue instanceof Number statusNumber)) {
                        continue;
                    }
                    long tournamentId = idNumber.longValue();
                    int status = statusNumber.intValue();
                    seasonStatusByTournamentId.put(tournamentId, status);
                    if (status >= 200 && status < 300 && bodyValue instanceof String body && !body.isBlank()) {
                        seasonJsonByTournamentId.putIfAbsent(tournamentId, body);
                    }
                }
            }

            Map<Long, List<SeasonDTO>> seasonsByTournamentId = new LinkedHashMap<>();
            List<Long> failedTournamentIds = new ArrayList<>();
            for (Long tournamentId : tournamentIds) {
                String body = seasonJsonByTournamentId.get(tournamentId);
                if (body == null) {
                    failedTournamentIds.add(tournamentId);
                    log.error("[SOFA_SCORE][LEAGUE] Season response missing: tournamentId={}, status={}",
                            tournamentId, seasonStatusByTournamentId.get(tournamentId));
                    continue;
                }
                SeasonsResponseDTO seasonResponse = objectMapper.readValue(body, SeasonsResponseDTO.class);
                seasonsByTournamentId.put(tournamentId,
                        seasonResponse.seasons() == null ? List.of() : seasonResponse.seasons());
                log.info("[SOFA_SCORE][LEAGUE] Seasons captured: tournamentId={}, seasons={}, status={}",
                        tournamentId, seasonsByTournamentId.get(tournamentId).size(),
                        seasonStatusByTournamentId.get(tournamentId));
            }

            if (!failedTournamentIds.isEmpty()) {
                throw new SofaScoreFetchException(
                        "Could not retrieve seasons for SofaScore tournament IDs " + failedTournamentIds);
            }

            return new SofaScoreLeagueCatalogueResult(tournaments, seasonsByTournamentId);
        } catch (SofaScoreFetchException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SofaScoreFetchException(
                    "Failed to fetch SofaScore leagues and seasons from one football page", exception);
        } finally {
            page.offResponse(responseListener);
        }
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
