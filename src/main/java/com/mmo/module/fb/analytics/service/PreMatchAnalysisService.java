package com.mmo.module.fb.analytics.service;

import java.time.Instant;
import java.util.List;

/**
 * Builds an evidence-backed pre-match report from persisted Core data.
 * Probabilities are descriptive model outputs, not betting advice.
 */
public interface PreMatchAnalysisService {

    PreMatchReport analyze(Long matchId);

    record PreMatchReport(
            Long matchId,
            Instant kickoff,
            String competition,
            String round,
            String status,
            TeamForm home,
            TeamForm away,
            HeadToHead headToHead,
            GoalModel goalModel,
            GoalModel xgModel,
            String dataQuality,
            List<String> dataWarnings,
            List<String> analysisNotes
    ) {
        public PreMatchReport {
            dataWarnings = dataWarnings == null ? List.of() : List.copyOf(dataWarnings);
            analysisNotes = analysisNotes == null ? List.of() : List.copyOf(analysisNotes);
        }
    }

    record TeamForm(
            Long teamId,
            String teamName,
            int matchesUsed,
            int venueMatchesUsed,
            double averageGoalsFor,
            double averageGoalsAgainst,
            double averagePoints,
            double venueAverageGoalsFor,
            double venueAverageGoalsAgainst,
            double winRate,
            double cleanSheetRate,
            double scoredRate,
            Double averageXgFor,
            Double averageXgAgainst,
            String recentForm
    ) {
    }

    record HeadToHead(
            int matchesUsed,
            int homeTeamWins,
            int draws,
            int awayTeamWins,
            double averageGoals,
            String summary,
            List<HeadToHeadMatch> recentMatches
    ) {
        public HeadToHead {
            recentMatches = recentMatches == null ? List.of() : List.copyOf(recentMatches);
        }
    }

    record HeadToHeadMatch(
            Long matchId,
            Instant kickoff,
            String homeTeam,
            String awayTeam,
            Integer homeScore,
            Integer awayScore
    ) {
    }

    record GoalModel(
            double expectedHomeGoals,
            double expectedAwayGoals,
            int homeSamples,
            int awaySamples,
            double homeWinProbability,
            double drawProbability,
            double awayWinProbability,
            String predictedOutcome
    ) {
    }
}
