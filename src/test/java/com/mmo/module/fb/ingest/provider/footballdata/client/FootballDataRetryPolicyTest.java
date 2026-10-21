package com.mmo.module.fb.ingest.provider.footballdata.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FootballDataRetryPolicyTest {
    private FootballDataRetryPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new FootballDataRetryPolicy();
    }

    @Test
    void retriesServerErrorsButStopsAfterConfiguredAttempts() {
        assertTrue(policy.isRetryableStatus(500));
        assertTrue(policy.isRetryableStatus(503));
        assertTrue(policy.shouldRetryStatus(503, 0));
        assertTrue(policy.shouldRetryStatus(503, 1));
        assertFalse(policy.shouldRetryStatus(503, 2));
    }

    @Test
    void retriesRateLimitOnlyOnce() {
        assertTrue(policy.isRetryableStatus(429));
        assertTrue(policy.shouldRetryStatus(429, 0));
        assertFalse(policy.shouldRetryStatus(429, 1));
        assertFalse(policy.shouldRetryStatus(429, 2));
    }

    @Test
    void doesNotRetryAuthenticationOrSubscriptionErrors() {
        assertFalse(policy.isRetryableStatus(401));
        assertFalse(policy.isRetryableStatus(403));
        assertFalse(policy.shouldRetryStatus(401, 0));
        assertFalse(policy.shouldRetryStatus(403, 0));
    }

    @Test
    void doesNotRetryOtherClientErrors() {
        assertFalse(policy.isRetryableStatus(400));
        assertFalse(policy.isRetryableStatus(404));
        assertFalse(policy.shouldRetryStatus(400, 0));
        assertFalse(policy.shouldRetryStatus(404, 0));
    }

    @Test
    void retriesTransientTransportFailuresOnlyWithinAttemptLimit() {
        assertTrue(policy.shouldRetryTransportFailure(0));
        assertTrue(policy.shouldRetryTransportFailure(1));
        assertFalse(policy.shouldRetryTransportFailure(2));
    }

    @Test
    void backoffIsBoundedAndIncludesJitter() {
        long first = policy.backoffMillis(0);
        long second = policy.backoffMillis(1);

        assertTrue(first >= 600 && first <= 900);
        assertTrue(second >= 1_100 && second <= 1_400);
    }
}
