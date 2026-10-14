package com.mmo.module.fb.ingest.provider.apifootball;

import com.fasterxml.jackson.databind.JsonNode;
import com.mmo.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;

import java.time.Duration;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ApiFootballClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(25);

    private final WebClient.Builder webClientBuilder;
    private final AppProperties appProperties;

    /**
     * Calls the official API-Sports API (https://v3.football.api-sports.io).
     * API key is supplied via APP_API_FOOTBALL_API_KEY / API_FOOTBALL_API_KEY,
     * never hard-coded in source.
     */
    public JsonNode get(String path, Map<String, ?> queryParams) {
        AppProperties.ApiFootball properties = appProperties.getApiFootball();
        if (properties == null || !StringUtils.hasText(properties.getApiKey())) {
            throw new IllegalStateException(
                    "API-Football key is missing. Set API_FOOTBALL_API_KEY environment variable.");
        }

        WebClient client = webClientBuilder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("x-apisports-key", properties.getApiKey())
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();

        JsonNode body = client.get()
                .uri(uriBuilder -> buildUri(uriBuilder, path, queryParams))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(REQUEST_TIMEOUT);

        if (body == null) {
            throw new IllegalStateException("Empty response from API-Football endpoint " + path);
        }

        JsonNode errors = body.path("errors");
        if (errors.isObject() && errors.size() > 0
                || errors.isArray() && !errors.isEmpty()) {
            throw new IllegalStateException("API-Football returned errors for " + path + ": " + errors);
        }
        return body;
    }

    public JsonNode get(String path) {
        return get(path, Map.of());
    }

    private java.net.URI buildUri(UriBuilder builder, String path, Map<String, ?> queryParams) {
        builder.path(path);
        if (queryParams != null) {
            queryParams.forEach((key, value) -> {
                if (value != null && StringUtils.hasText(String.valueOf(value))) {
                    builder.queryParam(key, value);
                }
            });
        }
        return builder.build();
    }
}
