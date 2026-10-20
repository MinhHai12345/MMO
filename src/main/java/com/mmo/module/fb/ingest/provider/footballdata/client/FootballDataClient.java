package com.mmo.module.fb.ingest.provider.footballdata.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmo.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class FootballDataClient {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(25);
    private static final String PLACEHOLDER_API_KEY = "mock";

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    public <T> T get(String path, Class<T> responseType, Object... uriVariables) {
        AppProperties.FootballData properties = appProperties.getFootballData();
        if (properties == null || !properties.isEnabled()) {
            throw new IllegalStateException("football-data.org integration is disabled. Set FOOTBALL_DATA_ENABLED=true.");
        }
        if (!StringUtils.hasText(properties.getApiKey())
                || PLACEHOLDER_API_KEY.equalsIgnoreCase(properties.getApiKey().trim())) {
            throw new IllegalStateException("football-data.org API token is not configured. Set FOOTBALL_DATA_API_KEY.");
        }
        if (!StringUtils.hasText(properties.getBaseUrl())) {
            throw new IllegalStateException("football-data.org base URL is missing.");
        }

        try {
            String json = webClientBuilder.baseUrl(properties.getBaseUrl())
                    .defaultHeader("X-Auth-Token", properties.getApiKey())
                    .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                    .build().get().uri(path, uriVariables)
                    .retrieve().bodyToMono(String.class).block(REQUEST_TIMEOUT);
            if (!StringUtils.hasText(json)) {
                throw new IllegalStateException("Empty response from football-data.org endpoint " + path);
            }
            return objectMapper.readValue(json, responseType);
        } catch (WebClientResponseException ex) {
            String message = "football-data.org HTTP " + ex.getStatusCode().value()
                    + " for " + path + ": " + ex.getResponseBodyAsString();
            if (ex.getStatusCode().value() == 429) {
                message += " (rate limit reached; honor provider request-counter headers and retry later)";
            }
            throw new IllegalStateException(message, ex);
        } catch (Exception ex) {
            if (ex instanceof IllegalStateException stateException) throw stateException;
            throw new IllegalStateException("Failed to call football-data.org endpoint " + path, ex);
        }
    }
}