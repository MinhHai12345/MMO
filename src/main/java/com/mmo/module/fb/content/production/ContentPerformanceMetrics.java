package com.mmo.module.fb.content.production;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Normalized metrics contract for platform adapters. Metric collection must
 * retain the provider and observation timestamp because platform definitions vary.
 */
public record ContentPerformanceMetrics(
        String platform,
        String externalPostId,
        Instant observedAt,
        long impressions,
        long views,
        long clicks,
        long likes,
        long comments,
        long shares,
        BigDecimal averageWatchSeconds,
        BigDecimal videoLengthSeconds) {

    public ContentPerformanceMetrics {
        if (platform == null || platform.isBlank()) {
            throw new IllegalArgumentException("platform is required");
        }
        if (externalPostId == null || externalPostId.isBlank()) {
            throw new IllegalArgumentException("externalPostId is required");
        }
        if (observedAt == null) {
            throw new IllegalArgumentException("observedAt is required");
        }
        if (impressions < 0 || views < 0 || clicks < 0 || likes < 0
                || comments < 0 || shares < 0) {
            throw new IllegalArgumentException("metrics cannot be negative");
        }
        if (averageWatchSeconds != null && averageWatchSeconds.signum() < 0) {
            throw new IllegalArgumentException("averageWatchSeconds cannot be negative");
        }
        if (videoLengthSeconds != null && videoLengthSeconds.signum() < 0) {
            throw new IllegalArgumentException("videoLengthSeconds cannot be negative");
        }
    }

    public BigDecimal clickThroughRatePercent() {
        return ratioPercent(clicks, impressions);
    }

    public BigDecimal engagementRatePercent() {
        return ratioPercent(likes + comments + shares, impressions > 0 ? impressions : views);
    }

    public BigDecimal averageWatchCompletionPercent() {
        if (averageWatchSeconds == null || videoLengthSeconds == null
                || videoLengthSeconds.signum() <= 0) {
            return null;
        }
        return averageWatchSeconds.multiply(BigDecimal.valueOf(100))
                .divide(videoLengthSeconds, 2, RoundingMode.HALF_UP)
                .min(BigDecimal.valueOf(100));
    }

    private static BigDecimal ratioPercent(long numerator, long denominator) {
        if (denominator <= 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }
}
