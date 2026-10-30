package com.mmo.module.fb.ingest.provider.client;

import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.http.HttpMethod;

import java.time.Duration;
import java.util.Map;

/** Immutable description of one provider HTTP request. URI must be absolute. */
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
    public ProviderHttpRequest {
        if (method == null) throw new IllegalArgumentException("HTTP method is required");
        if (uri == null || uri.isBlank()) throw new IllegalArgumentException("Request URI is required");
        if (responseType == null) throw new IllegalArgumentException("Response type is required");
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        queryParams = queryParams == null ? Map.of() : Map.copyOf(queryParams);
        timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
        maxInMemorySize = maxInMemorySize <= 0 ? 10 * 1024 * 1024 : maxInMemorySize;
    }
}
