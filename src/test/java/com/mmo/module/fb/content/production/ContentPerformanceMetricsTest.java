package com.mmo.module.fb.content.production;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ContentPerformanceMetricsTest {

    @Test
    void computesRatesAndCapsWatchCompletionAtOneHundredPercent() {
        var metrics = new ContentPerformanceMetrics(
                "YOUTUBE", "post-1", Instant.parse("2026-10-22T00:00:00Z"),
                1000, 500, 80, 40, 10, 5, new BigDecimal("75"), new BigDecimal("60"));

        assertEquals(new BigDecimal("8.00"), metrics.clickThroughRatePercent());
        assertEquals(new BigDecimal("5.50"), metrics.engagementRatePercent());
        assertEquals(new BigDecimal("100.00"), metrics.averageWatchCompletionPercent());
    }

    @Test
    void returnsNullWhenRateDenominatorIsUnavailable() {
        var metrics = new ContentPerformanceMetrics(
                "INSTAGRAM", "post-2", Instant.parse("2026-10-22T00:00:00Z"),
                0, 0, 0, 0, 0, 0, null, null);
        assertNull(metrics.clickThroughRatePercent());
        assertNull(metrics.engagementRatePercent());
        assertNull(metrics.averageWatchCompletionPercent());
    }

    @Test
    void rejectsNegativeMetrics() {
        assertThrows(IllegalArgumentException.class, () -> new ContentPerformanceMetrics(
                "X", "post-3", Instant.now(), -1, 0, 0, 0, 0, 0, null, null));
    }
}
