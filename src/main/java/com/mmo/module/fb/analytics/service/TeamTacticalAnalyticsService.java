package com.mmo.module.fb.analytics.service;

import java.time.Instant;
import java.util.List;

/**
 * Aggregates match-level advanced statistics into a recent team tactical profile.
 * All metrics are descriptive historical observations, not tactical labels or forecasts.
 */
public interface TeamTacticalAnalyticsService {

    TeamTacticalProfile analyze(Long teamId, Long leagueId, int historySize);

    record TeamTacticalProfile(
            Long teamId,
            String teamName,
            Long leagueId,
            Instant generatedAt,
            int requestedHistorySize,
            int matchesUsed,
            String dataQuality,
            MetricAverage possession,
            MetricAverage ppda,
            MetricAverage fieldTilt,
            MetricAverage xgFor,
            MetricAverage xgAgainst,
            MetricAverage nonPenaltyXgFor,
            MetricAverage nonPenaltyXgAgainst,
            MetricAverage xgChain,
            MetricAverage xgBuildup,
            MetricAverage setPieceXg,
            MetricAverage counterAttackXg,
            MetricAverage shots,
            MetricAverage shotsOnTarget,
            MetricAverage shotsFromBox,
            MetricAverage shotsOutsideBox,
            MetricAverage deepCompletions,
            MetricAverage finalThirdEntries,
            MetricAverage passAccuracy,
            MetricTrend xgForTrend,
            MetricTrend xgAgainstTrend,
            MetricTrend shotsTrend,
            MetricTrend possessionTrend,
            MetricTrend ppdaTrend,
            List<MatchTacticalSample> recentMatches,
            List<String> observations,
            List<String> dataWarnings
    ) {
        public TeamTacticalProfile {
            recentMatches = recentMatches == null ? List.of() : List.copyOf(recentMatches);
            observations = observations == null ? List.of() : List.copyOf(observations);
            dataWarnings = dataWarnings == null ? List.of() : List.copyOf(dataWarnings);
        }
    }

    /** Average and actual non-null sample count; missing provider values are never treated as zero. */
    record MetricAverage(Double average, int samples) {
    }

    /** Compares the newest half of the sample with the preceding half. */
    record MetricTrend(
            Double recentAverage,
            Double previousAverage,
            Double change,
            String direction,
            int recentSamples,
            int previousSamples
    ) {
    }

    record MatchTacticalSample(
            Long matchId,
            Instant kickoff,
            String opponent,
            boolean home,
            Integer goalsFor,
            Integer goalsAgainst,
            Double possession,
            Double ppda,
            Double fieldTilt,
            Double xgFor,
            Double xgAgainst,
            Integer shots,
            Integer shotsOnTarget,
            Integer shotsFromBox,
            Integer finalThirdEntries,
            Integer deepCompletions
    ) {
    }
}
