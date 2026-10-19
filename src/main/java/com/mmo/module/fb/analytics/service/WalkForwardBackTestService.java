package com.mmo.module.fb.analytics.service;

import java.time.Instant;

public interface WalkForwardBackTestService {

    BackTestReport run(Instant fromInclusive, Instant toExclusive);

    record MetricSnapshot(double brierScore, double logLoss, double accuracy, double expectedCalibrationError) {
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
            String status) {
    }
}
