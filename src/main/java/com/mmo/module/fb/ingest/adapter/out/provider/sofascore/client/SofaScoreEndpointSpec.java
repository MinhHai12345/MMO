package com.mmo.module.fb.ingest.adapter.out.provider.sofascore.client;

import java.util.Objects;

/**
 * Describes one API response expected from a single SofaScore page navigation.
 * Multiple specs can be collected from the same page load.
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
    }

    public static SofaScoreEndpointSpec required(String endpoint, Class<?> responseType) {
        return new SofaScoreEndpointSpec(endpoint, responseType, true);
    }

    public static SofaScoreEndpointSpec optional(String endpoint, Class<?> responseType) {
        return new SofaScoreEndpointSpec(endpoint, responseType, false);
    }
}
