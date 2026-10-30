package com.mmo.module.fb.ingest.provider.apifootball.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.apifootball.dto.ApiFootballResponse;
import com.mmo.module.fb.ingest.provider.client.AbstractProviderClient;
import com.mmo.module.fb.ingest.provider.client.ProviderHttpClient;
import com.mmo.module.fb.ingest.provider.client.ProviderHttpException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** API-Football endpoint client; shared HTTP execution is delegated to ProviderHttpClient. */
@Component
public class ApiFootballClient extends AbstractProviderClient {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(25);
    private static final int MAX_IN_MEMORY_SIZE_BYTES = 10 * 1024 * 1024;
    private static final String PLACEHOLDER_API_KEY = "mock";

    public ApiFootballClient(ProviderHttpClient httpClient, AppProperties appProperties) {
        super(appProperties, httpClient);
    }

    public <T> ApiFootballResponse<T> get(
            String path, Map<String, ?> queryParams,
            TypeReference<ApiFootballResponse<T>> responseType) {
        AppProperties.ProviderConfig config = providerConfig(Provider.API_FOOTBALL);
        if (!StringUtils.hasText(config.getApiKey())
                || PLACEHOLDER_API_KEY.equalsIgnoreCase(config.getApiKey().trim())) {
            throw new IllegalStateException(
                    "API-Football API key is not configured. Set API_FOOTBALL_API_KEY environment variable.");
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("x-apisports-key", config.getApiKey());
        headers.put(HttpHeaders.ACCEPT, "application/json");
        ApiFootballResponse<T> response;
        try {
            response = exchange(Provider.API_FOOTBALL, HttpMethod.GET, path, headers,
                    queryParams, null, responseType, REQUEST_TIMEOUT, MAX_IN_MEMORY_SIZE_BYTES);
        } catch (ProviderHttpException ex) {
            throw new IllegalStateException("API-Football HTTP " + ex.getStatusCode()
                    + " for " + path + ": " + ex.getResponseBody(), ex);
        }

        if (response == null) {
            throw new IllegalStateException("Could not deserialize API-Football response for " + path);
        }
        if (response.hasErrors()) {
            String message = "API-Football returned errors for " + path + ": " + response.errors();
            String normalizedErrors = String.valueOf(response.errors()).toLowerCase(java.util.Locale.ROOT);
            if (normalizedErrors.contains("free plans do not have access")
                    || normalizedErrors.contains("plan does not have access")) {
                throw new ApiFootballPlanRestrictionException(message
                        + ". The configured subscription cannot access this season; "
                        + "skip it or use an API-Football plan that includes it.");
            }
            throw new IllegalStateException(message);
        }
        return response;
    }
}
