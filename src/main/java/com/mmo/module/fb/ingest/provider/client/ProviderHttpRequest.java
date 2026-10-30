package com.mmo.module.fb.ingest.provider.client;

import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.http.HttpMethod;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable description of one provider HTTP request. URI must be an absolute HTTP(S) URI. */
public record ProviderHttpRequest<T>(
        HttpMethod method,
        String uri,
        Map<String, String> headers,
        Map<String, ?> queryParams,
        Object body,
        TypeReference<T> responseType,
        Duration timeout,
        int maxInMemorySize
) {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final int DEFAULT_MAX_IN_MEMORY_SIZE = 10 * 1024 * 1024;

    public ProviderHttpRequest {
        if (method == null) {
            throw new IllegalArgumentException("HTTP method is required");
        }
        if (uri == null || uri.isBlank()) {
            throw new IllegalArgumentException("Request URI is required");
        }
        URI parsedUri;
        try {
            parsedUri = URI.create(uri.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Request URI is invalid: " + uri, ex);
        }
        if (!parsedUri.isAbsolute()
                || parsedUri.getHost() == null
                || !("http".equalsIgnoreCase(parsedUri.getScheme())
                || "https".equalsIgnoreCase(parsedUri.getScheme()))) {
            throw new IllegalArgumentException("Request URI must be an absolute HTTP(S) URI");
        }
        uri = parsedUri.toASCIIString();
        if (responseType == null) {
            throw new IllegalArgumentException("Response type is required");
        }
        if (timeout == null) {
            timeout = DEFAULT_TIMEOUT;
        } else if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Request timeout must be positive");
        }
        if (maxInMemorySize <= 0) {
            maxInMemorySize = DEFAULT_MAX_IN_MEMORY_SIZE;
        }

        headers = immutableHeaders(headers);
        queryParams = immutableQueryParams(queryParams);
    }

    private static Map<String, String> immutableHeaders(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, String> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("HTTP header name must not be blank");
            }
            if (value == null) {
                throw new IllegalArgumentException("HTTP header value must not be null: " + key);
            }
            copy.put(key, value);
        });
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, ?> immutableQueryParams(Map<String, ?> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Query parameter name must not be blank");
            }
            // Null values are intentionally retained and omitted by the transport.
            copy.put(key, value);
        });
        return Collections.unmodifiableMap(copy);
    }
}
