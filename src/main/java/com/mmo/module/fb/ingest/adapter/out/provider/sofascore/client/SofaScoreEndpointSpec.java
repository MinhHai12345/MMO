package com.mmo.module.fb.ingest.adapter.out.provider.sofascore.client;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;

/**
 * Describes one API response expected from a single SofaScore page navigation.
 * The endpoint is matched against the end of the response path, not an arbitrary substring.
 */
public record SofaScoreEndpointSpec(
        String endpoint,
        Class<?> responseType,
        boolean required) {

    public SofaScoreEndpointSpec {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("SofaScore endpoint must not be blank");
        }
        Objects.requireNonNull(responseType, "SofaScore response type is required");
        endpoint = normalizePath(endpoint);
    }

    public static SofaScoreEndpointSpec required(String endpoint, Class<?> responseType) {
        return new SofaScoreEndpointSpec(endpoint, responseType, true);
    }

    public static SofaScoreEndpointSpec optional(String endpoint, Class<?> responseType) {
        return new SofaScoreEndpointSpec(endpoint, responseType, false);
    }

    public boolean matchesUrl(String responseUrl) {
        if (responseUrl == null || responseUrl.isBlank()) {
            return false;
        }
        try {
            String responsePath = normalizePath(URI.create(responseUrl).getRawPath());
            return responsePath.endsWith(endpoint);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String normalizePath(String value) {
        String path = value.trim();
        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path.toLowerCase(Locale.ROOT);
    }
}
