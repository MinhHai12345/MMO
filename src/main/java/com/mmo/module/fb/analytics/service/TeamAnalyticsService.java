package com.mmo.module.fb.analytics.service;

import java.time.Instant;
import java.util.List;

/**
 * Read-only team performance analytics calculated from persisted core match data.
 */
public interface TeamAnalyticsService {

    TeamPerformance analyze(Long teamId, int historySize);

    record TeamPerformance(
            Long teamId,
            String teamName,
            Instant generatedAt,
            int requestedHistorySize,
            int matchesUsed,
            int homeMatches,
            int awayMatches,
            int wins,
            int draws,
            int losses,
            double winRate,
            double pointsPerMatch,
            double goalsForPerMatch,
            double goalsAgainstPerMatch,
            double goalDifferencePerMatch,
            double cleanSheetRate,
            double scoredRate,
            int xgMatches,
            Double xgForPerMatch,
            Double xgAgainstPerMatch,
            Double goalsMinusXgPerMatch,
            String recentForm,
            List<MatchSample> recentMatches,
            List<String> dataWarnings
    ) {
        public TeamPerformance {
            recentMatches = recentMatches == null ? List.of() : List.copyOf(recentMatches);
            dataWarnings = dataWarnings == null ? List.of() : List.copyOf(dataWarnings);
        }
    }

    record MatchSample(
            Long matchId,
            Instant kickoff,
            String opponent,
            boolean home,
            Integer goalsFor,
            Integer goalsAgainst,
            Double xgFor,
            Double xgAgainst,
            String result
    ) {}
}
