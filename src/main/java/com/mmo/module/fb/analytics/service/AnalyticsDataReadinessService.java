package com.mmo.module.fb.analytics.service;

import java.time.Instant;
import java.util.List;

/**
 * Reports whether persisted match data is sufficient for the football analytics pipeline.
 */
public interface AnalyticsDataReadinessService {

    DataReadinessReport getReadiness();

    record SeasonReadiness(
            Long seasonId,
            String league,
            String season,
            long totalMatches,
            long finishedMatches,
            long finishedWithScores,
            long finishedWithExpectedGoals,
            long upcomingMatches,
            double scoreCoverage,
            double expectedGoalsCoverage,
            String goalsModelReadiness,
            String expectedGoalsModelReadiness,
            List<String> issues) {
        public SeasonReadiness {
            issues = issues == null ? List.of() : List.copyOf(issues);
        }
    }

    record DataReadinessReport(
            Instant generatedAt,
            int seasons,
            long totalMatches,
            long finishedMatches,
            long finishedWithScores,
            long finishedWithExpectedGoals,
            long upcomingMatches,
            List<SeasonReadiness> breakdown) {
        public DataReadinessReport {
            breakdown = breakdown == null ? List.of() : List.copyOf(breakdown);
        }
    }
}
