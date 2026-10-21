package com.mmo.module.fb.ingest.provider.footballdata.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FootballDataHttpErrorClassifierTest {
    private final FootballDataHttpErrorClassifier classifier = new FootballDataHttpErrorClassifier();

    @Test
    void classifies401AsAuthenticationFailureWithoutRetrySemantics() {
        RuntimeException error = classifier.classify(401, "unauthorized", null);

        assertInstanceOf(FootballDataAuthenticationException.class, error);
        assertTrue(error.getMessage().contains("FOOTBALL_DATA_API_KEY"));
    }

    @Test
    void classifies403AsRestrictedResource() {
        RuntimeException error = classifier.classify(403, "forbidden", null);

        assertInstanceOf(FootballDataRestrictedResourceException.class, error);
    }

    @Test
    void classifies429AsRateLimit() {
        RuntimeException error = classifier.classify(429, "quota exceeded", null);

        assertInstanceOf(FootballDataRateLimitException.class, error);
    }

    @Test
    void preserves5xxAndOtherHttpStatuses() {
        FootballDataRequestException serverError = assertInstanceOf(
                FootballDataRequestException.class,
                classifier.classify(503, "service unavailable", null));
        FootballDataRequestException clientError = assertInstanceOf(
                FootballDataRequestException.class,
                classifier.classify(404, "not found", null));

        assertEquals(503, serverError.getStatusCode());
        assertEquals(404, clientError.getStatusCode());
    }
}
