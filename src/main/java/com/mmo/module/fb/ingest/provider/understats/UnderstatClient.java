package com.mmo.module.fb.ingest.provider.understats;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.enums.Provider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;

/**
 * HTTP client for Understat's JSON AJAX endpoints.
 *
 * The endpoint is public but may enforce session/access controls. This client deliberately
 * does not hardcode browser cookies or attempt to bypass those controls.
 */
@Component
@RequiredArgsConstructor
public class UnderstatClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_IN_MEMORY_SIZE_BYTES = 10 * 1024 * 1024;
    private static final String ACCEPT_HEADER = "application/json, text/javascript, */*; q=0.01";

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    public <T> T fetchLeagueData(String leagueSlug, int season, TypeReference<T> responseType) {
        validateSlug(leagueSlug, "league");
        if (season < 2014) {
            throw new IllegalArgumentException("Understat season must be 2014 or later");
        }

        String path = UriComponentsBuilder.fromPath("/getLeagueData/{league}/{season}")
                .buildAndExpand(leagueSlug, season).encode().toUriString();
        String referer = UriComponentsBuilder
                .fromUriString("https://understat.com/league/{league}/{season}")
                .buildAndExpand(leagueSlug, season).encode().toUriString();
        return fetchJson(path, referer, responseType);
    }

    public <T> T fetchTeamData(String teamSlug, int season, TypeReference<T> responseType) {
        validateSlug(teamSlug, "team");
        if (season < 2014) {
            throw new IllegalArgumentException("Understat season must be 2014 or later");
        }

        String path = UriComponentsBuilder.fromPath("/getTeamData/{team}/{season}")
                .buildAndExpand(teamSlug, season).encode().toUriString();
        String referer = UriComponentsBuilder
                .fromUriString("https://understat.com/team/{team}/{season}")
                .buildAndExpand(teamSlug, season).encode().toUriString();
        return fetchJson(path, referer, responseType);
    }

    private <T> T fetchJson(String path, String referer, TypeReference<T> responseType) {
        AppProperties.ProviderConfig config = appProperties.getIngestion()
                .getProviderConfig().get(Provider.UNDER_STATS);
        if (config == null || !config.isEnabled()) {
            throw new IllegalStateException(
                    "Understat integration is disabled. Set UNDER_STATS_ENABLED=true.");
        }
        if (!StringUtils.hasText(config.getUri())) {
            throw new IllegalStateException(
                    "Understat base URL is missing. Set UNDER_STAT_BASE_URL, e.g. https://understat.com.");
        }

        String responseBody;
        try {
            responseBody = webClientBuilder.clone()
                    .baseUrl(stripTrailingSlash(config.getUri()))
                    .defaultHeader(HttpHeaders.ACCEPT, ACCEPT_HEADER)
                    .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9")
                    .defaultHeader(HttpHeaders.REFERER, referer)
                    .defaultHeader("X-Requested-With", "XMLHttpRequest")
                    .defaultHeader(HttpHeaders.USER_AGENT,
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                                    + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE_BYTES))
                    .build()
                    .get()
                    .uri(path)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(REQUEST_TIMEOUT);
        } catch (WebClientResponseException ex) {
            throw new IllegalStateException("Understat HTTP " + ex.getStatusCode().value()
                    + " for " + path + ". Check endpoint availability and access permissions.", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to fetch Understat JSON endpoint " + path, ex);
        }

        if (!StringUtils.hasText(responseBody)) {
            throw new IllegalStateException("Understat returned an empty response for " + path);
        }
        String trimmed = responseBody.stripLeading();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            throw new IllegalStateException("Understat returned non-JSON content for " + path
                    + ". Access may be blocked or a challenge page was returned.");
        }

        try {
            return objectMapper.readValue(responseBody, responseType);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not deserialize Understat JSON response from " + path, ex);
        }
    }

    private static String stripTrailingSlash(String value) {
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static void validateSlug(String slug, String resource) {
        if (!StringUtils.hasText(slug) || slug.contains("/") || slug.contains("..")) {
            throw new IllegalArgumentException("A valid Understat " + resource + " slug is required");
        }
    }
}
