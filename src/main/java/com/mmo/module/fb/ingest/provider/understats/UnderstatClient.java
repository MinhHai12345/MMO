package com.mmo.module.fb.ingest.provider.understats;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmo.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;

/** HTTP client for Understat's public league/team pages. */
@Component
@RequiredArgsConstructor
public class UnderstatClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_IN_MEMORY_SIZE_BYTES = 10 * 1024 * 1024;

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;
    private final UnderstatPageParser pageParser;

    public <T> T fetchLeaguePageData(String leagueSlug, int season, String variableName,
                                     TypeReference<T> responseType) {
        validateSlug(leagueSlug, "league");
        String path = UriComponentsBuilder.fromPath("/league/{league}/{season}")
                .buildAndExpand(leagueSlug, season).encode().toUriString();
        return fetchEmbeddedData(path, variableName, responseType);
    }

    public <T> T fetchTeamPageData(String teamSlug, int season, String variableName,
                                   TypeReference<T> responseType) {
        validateSlug(teamSlug, "team");
        String path = UriComponentsBuilder.fromPath("/team/{team}/{season}")
                .buildAndExpand(teamSlug, season).encode().toUriString();
        return fetchEmbeddedData(path, variableName, responseType);
    }

    private <T> T fetchEmbeddedData(String path, String variableName, TypeReference<T> responseType) {
        AppProperties.ProviderConfig config = appProperties.getIngestion()
                .getProviderConfig().get(com.mmo.module.fb.ingest.enums.Provider.UNDER_STATS);
        if (config == null || !config.isEnabled()) {
            throw new IllegalStateException(
                    "Understat integration is disabled. Set UNDER_STATS_ENABLED=true.");
        }
        if (!StringUtils.hasText(config.getUri())) {
            throw new IllegalStateException(
                    "Understat base URL is missing. Set UNDER_STAT_BASE_URL, e.g. https://understat.com.");
        }

        String html;
        try {
            html = webClientBuilder.clone()
                    .baseUrl(stripTrailingSlash(config.getUri()))
                    .defaultHeader(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml")
                    .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE_BYTES))
                    .build()
                    .get()
                    .uri(path)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(REQUEST_TIMEOUT);
        } catch (WebClientResponseException ex) {
            throw new IllegalStateException("Understat HTTP " + ex.getStatusCode().value()
                    + " for " + path + ": " + ex.getResponseBodyAsString(), ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to fetch Understat page " + path, ex);
        }

        try {
            return objectMapper.readValue(pageParser.extractJson(html, variableName), responseType);
        } catch (Exception ex) {
            if (ex instanceof IllegalStateException stateException) {
                throw stateException;
            }
            throw new IllegalStateException(
                    "Could not parse Understat page data '" + variableName + "' from " + path, ex);
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
