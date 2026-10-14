package com.mmo.module.fb.ingest.provider.apifootball.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.provider.apifootball.dto.ApiFootballResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriBuilder;

import java.time.Duration;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ApiFootballClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(25);

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    public <T> ApiFootballResponse<T> get(
            String path,
            Map<String, ?> queryParams,
            TypeReference<ApiFootballResponse<T>> responseType
    ) {
        AppProperties.ApiFootball properties = appProperties.getApiFootball();
        if (properties == null || !StringUtils.hasText(properties.getApiKey())) {
            throw new IllegalStateException(
                    "API-Football key is missing. Set API_FOOTBALL_API_KEY environment variable.");
        }

        try {
            String json = webClientBuilder
                    .baseUrl(properties.getBaseUrl())
                    .defaultHeader("x-apisports-key", properties.getApiKey())
                    .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                    .build()
                    .get()
                    .uri(uriBuilder -> buildUri(uriBuilder, path, queryParams))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(REQUEST_TIMEOUT);

            if (!StringUtils.hasText(json)) {
                throw new IllegalStateException("Empty response from API-Football endpoint " + path);
            }

            ApiFootballResponse<T> response = objectMapper.readValue(json, responseType);
            if (response == null) {
                throw new IllegalStateException("Could not deserialize API-Football response for " + path);
            }
            if (response.hasErrors()) {
                throw new IllegalStateException("API-Football returned errors for " + path + ": " + response.errors());
            }
            return response;
        } catch (WebClientResponseException ex) {
            throw new IllegalStateException("API-Football HTTP " + ex.getStatusCode().value()
                    + " for " + path + ": " + ex.getResponseBodyAsString(), ex);
        } catch (Exception ex) {
            if (ex instanceof IllegalStateException stateException) throw stateException;
            throw new IllegalStateException("Failed to call API-Football endpoint " + path, ex);
        }
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
