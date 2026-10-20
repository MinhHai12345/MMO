package com.mmo.module.fb.analytics.service;

import java.time.Instant;

public interface TeamFormBackTestService {

    TeamFormBackTestReport run(Instant fromInclusive, Instant toExclusive);

    record MetricSnapshot(int matches, double brierScore, double logLoss, double accuracy) {
    }

    record TeamFormBackTestReport(
            Instant from,
            Instant to,
            int candidateMatches,
            int evaluatedMatches,
            int skippedInsufficientHistory,
            MetricSnapshot uniformBaseline,
            MetricSnapshot teamFormModel,
            String status) {
    }
}
