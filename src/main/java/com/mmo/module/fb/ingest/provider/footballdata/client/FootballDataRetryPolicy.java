package com.mmo.module.fb.ingest.provider.footballdata.client;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Shared retry policy for all football-data.org resources.
 *
 * Client-side request pacing remains responsible for the provider quota; this policy only
 * controls transient failure retries. Authentication, subscription and ordinary 4xx failures
 * must never be retried automatically.
 */
public class FootballDataRetryPolicy {
    public static final int MAX_ATTEMPTS = 3;

    public boolean isRetryableStatus(int statusCode) {
        return statusCode == 429 || statusCode >= 500 && statusCode <= 599;
    }

    public boolean shouldRetryStatus(int statusCode, int attempt) {
        if (attempt >= MAX_ATTEMPTS - 1) return false;
        // A 429 is retried only once because the provider may impose a long quota cooldown.
        if (statusCode == 429) return attempt == 0;
        return statusCode >= 500 && statusCode <= 599;
    }

    public boolean shouldRetryTransportFailure(int attempt) {
        return attempt < MAX_ATTEMPTS - 1;
    }

    public long backoffMillis(int attempt) {
        long base = Math.min(4_000L, 500L << Math.max(0, attempt));
        return base + ThreadLocalRandom.current().nextLong(100L, 401L);
    }
}
