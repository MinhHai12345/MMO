package com.mmo.module.fb.ingest.provider.footballdata.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FootballDataRetryPolicyTest {
    private final FootballDataRetryPolicy policy = new FootballDataRetryPolicy();

    @Test
    void retriesRateLimitOnlyOnceAndNeverRetriesAuthenticationOrClientErrors() {
        assertThat(policy.shouldRetryStatus(429, 0)).isTrue();
        assertThat(policy.shouldRetryStatus(429, 1)).isFalse();
        assertThat(policy.shouldRetryStatus(401, 0)).isFalse();
        assertThat(policy.shouldRetryStatus(403, 0)).isFalse();
        assertThat(policy.shouldRetryStatus(404, 0)).isFalse();
    }

    @Test
    void retriesServerErrorsAndTransportFailuresWithinAttemptLimit() {
        assertThat(policy.shouldRetryStatus(500, 0)).isTrue();
        assertThat(policy.shouldRetryStatus(503, 1)).isTrue();
        assertThat(policy.shouldRetryStatus(503, 2)).isFalse();
        assertThat(policy.shouldRetryTransportFailure(0)).isTrue();
        assertThat(policy.shouldRetryTransportFailure(2)).isFalse();
    }

    @Test
    void appliesBoundedExponentialBackoffWithJitter() {
        assertThat(policy.backoffMillis(0)).isBetween(600L, 900L);
        assertThat(policy.backoffMillis(1)).isBetween(1_100L, 1_400L);
        assertThat(policy.backoffMillis(5)).isBetween(4_100L, 4_400L);
    }
}
