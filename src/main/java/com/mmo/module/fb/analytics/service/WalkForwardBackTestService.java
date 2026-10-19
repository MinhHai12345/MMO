package com.mmo.module.fb.analytics.service;

import java.time.Instant;
import java.util.List;

public interface WalkForwardBackTestService {

    BackTestReport run(Instant fromInclusive, Instant toExclusive);

    record MetricSnapshot(double brierScore, double logLoss, double accuracy, double expectedCalibrationError) {
    }

    record GroupMetricSnapshot(
            String league,
            String season,
            int testedMatches,
            MetricSnapshot baseline,
            MetricSnapshot raw,
            MetricSnapshot calibrated,
            int dixonColesMatches,
            MetricSnapshot dixonColes) {
    }

    record BackTestReport(
            Instant from,
            Instant to,
            int testedMatches,
            int calibratedMatches,
            int calibrationHistorySamples,
            MetricSnapshot baseline,
            MetricSnapshot raw,
            MetricSnapshot calibrated,
            int dixonColesMatches,
            MetricSnapshot dixonColes,
            List<GroupMetricSnapshot> breakdown,
            String status) {
        public BackTestReport {
            breakdown = breakdown == null ? List.of() : List.copyOf(breakdown);
        }
    }
}
