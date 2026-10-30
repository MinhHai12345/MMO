package com.mmo.module.fb.ingest.provider.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.enums.Provider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Common provider configuration and request construction for REST-backed data sources. */
@RequiredArgsConstructor
public abstract class AbstractProviderClient {
    protected final AppProperties appProperties;
    protected final ProviderHttpClient httpClient;

    protected AppProperties.ProviderConfig providerConfig(Provider provider) {
        AppProperties.ProviderConfig config = appProperties.getIngestion().getProviderConfig().get(provider);
        if (config == null || !config.isEnabled()) {
            throw new IllegalStateException(provider + " integration is missing or disabled");
        }
        if (!StringUtils.hasText(config.getUri())) {
            throw new IllegalStateException(provider + " base URI is not configured");
        }
        return config;
    }

    protected <T> T exchange(Provider provider, HttpMethod method, String path,
                             Map<String, String> headers, Map<String, ?> queryParams,
                             Object body, TypeReference<T> responseType,
                             Duration timeout, int maxInMemorySize) {
        AppProperties.ProviderConfig config = providerConfig(provider);
        String baseUri = stripTrailingSlash(config.getUri());
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        Map<String, String> requestHeaders = new LinkedHashMap<>();
        requestHeaders.put(HttpHeaders.ACCEPT, "application/json");
        if (headers != null) requestHeaders.putAll(headers);
        ProviderHttpRequest<T> request = new ProviderHttpRequest<>(method,
                baseUri + normalizedPath, requestHeaders, queryParams, body, responseType,
                timeout, maxInMemorySize);
        return httpClient.execute(request);
    }

    protected static String stripTrailingSlash(String value) {
        String normalized = value.trim();
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }
}
