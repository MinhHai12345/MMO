package com.mmo.module.fb.analytics.service;

import java.time.Instant;

public interface WalkForwardBacktestService {

    BacktestReport run(Instant fromInclusive, Instant toExclusive);

    record MetricSnapshot(double brierScore, double logLoss, double accuracy) {
    }

    record BacktestReport(
            Instant from,
            Instant to,
            int testedMatches,
            int calibratedMatches,
            int calibrationHistorySamples,
            MetricSnapshot raw,
            MetricSnapshot calibrated,
            String status) {
    }
}
