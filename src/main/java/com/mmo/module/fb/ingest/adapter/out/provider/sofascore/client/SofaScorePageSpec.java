package com.mmo.module.fb.ingest.adapter.out.provider.sofascore.client;

import java.util.List;
import java.util.Objects;

/**
 * Complete network-capture contract for one SofaScore page navigation.
 * The page URL and all API responses expected from that navigation are immutable and colocated.
 */
public record SofaScorePageSpec(
        String loadPageUrl,
        List<SofaScoreEndpointSpec> endpointSpecs) {

    public SofaScorePageSpec {
        if (loadPageUrl == null || loadPageUrl.isBlank()) {
            throw new IllegalArgumentException("SofaScore page URL must not be blank");
        }
        Objects.requireNonNull(endpointSpecs, "SofaScore endpoint specs are required");
        endpointSpecs = List.copyOf(endpointSpecs);
        if (endpointSpecs.isEmpty()) {
            throw new IllegalArgumentException("At least one SofaScore endpoint must be configured for a page");
        }
        if (endpointSpecs.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("SofaScore endpoint specs must not contain null values");
        }
        long requiredCount = endpointSpecs.stream().filter(SofaScoreEndpointSpec::required).count();
        if (requiredCount == 0) {
            throw new IllegalArgumentException("At least one required SofaScore endpoint must be configured");
        }
        long distinctCount = endpointSpecs.stream().map(SofaScoreEndpointSpec::endpoint).distinct().count();
        if (distinctCount != endpointSpecs.size()) {
            throw new IllegalArgumentException("SofaScore endpoint matchers must be unique within a page");
        }
    }

    public static SofaScorePageSpec of(
            String loadPageUrl,
            SofaScoreEndpointSpec... endpointSpecs) {
        return new SofaScorePageSpec(loadPageUrl, List.of(endpointSpecs));
    }

    public SofaScoreEndpointSpec singleRequiredEndpoint() {
        List<SofaScoreEndpointSpec> required = endpointSpecs.stream()
                .filter(SofaScoreEndpointSpec::required)
                .toList();
        if (required.size() != 1) {
            throw new IllegalStateException("Expected exactly one required endpoint but found " + required.size());
        }
        return required.get(0);
    }

    public SofaScoreEndpointSpec endpoint(String matcher) {
        return endpointSpecs.stream()
                .filter(spec -> spec.endpoint().equals(normalize(matcher)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Endpoint is not part of this page spec: " + matcher));
    }

    private static String normalize(String endpoint) {
        if (endpoint == null) return "";
        String value = endpoint.trim();
        while (value.length() > 1 && value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.startsWith("/") ? value.toLowerCase(java.util.Locale.ROOT)
                : "/" + value.toLowerCase(java.util.Locale.ROOT);
    }
}
