package com.mmo.module.fb.ingest.adapter.out.provider.sofascore.client;

import java.util.List;

/**
 * Complete network-capture contract for one SofaScore page navigation.
 * A page URL and all API endpoints expected from that page are kept together.
 */
public record SofaScorePageSpec(
        String loadPageUrl,
        List<SofaScoreEndpointSpec> endpointSpecs) {

    public SofaScorePageSpec {
        if (loadPageUrl == null || loadPageUrl.isBlank()) {
            throw new IllegalArgumentException("SofaScore page URL must not be blank");
        }
        if (endpointSpecs == null || endpointSpecs.isEmpty()) {
            throw new IllegalArgumentException("At least one SofaScore endpoint must be configured for a page");
        }
        endpointSpecs = List.copyOf(endpointSpecs);
    }

    public static SofaScorePageSpec of(
            String loadPageUrl,
            SofaScoreEndpointSpec... endpointSpecs) {
        return new SofaScorePageSpec(loadPageUrl, List.of(endpointSpecs));
    }
}
