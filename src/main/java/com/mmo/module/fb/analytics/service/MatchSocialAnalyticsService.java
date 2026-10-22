package com.mmo.module.fb.analytics.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface MatchSocialAnalyticsService {

    MatchSocialInsight analyze(Long matchId);

    List<ContentCandidate> discover(Instant from, Instant to, Long leagueId, int limit);

    record MatchSocialInsight(
            Long matchId,
            Instant kickoff,
            String competition,
            String season,
            String round,
            String venue,
            String status,
            TeamInsight home,
            TeamInsight away,
            PredictionInsight prediction,
            MarketInsight market,
            MatchupInsight matchup,
            List<String> editorialAngles,
            List<String> dataWarnings) {
        public MatchSocialInsight {
            editorialAngles = editorialAngles == null ? List.of() : List.copyOf(editorialAngles);
            dataWarnings = dataWarnings == null ? List.of() : List.copyOf(dataWarnings);
        }
    }

    record TeamInsight(
            Long teamId,
            String name,
            String logoUrl,
            Integer leaguePosition,
            Integer points,
            String standingsForm,
            String recentForm,
            int matchesAnalyzed,
            int venueMatchesAnalyzed,
            BigDecimal averageGoalsFor,
            BigDecimal averageGoalsAgainst,
            BigDecimal averagePoints,
            BigDecimal winRate,
            BigDecimal cleanSheetRate,
            BigDecimal scoredRate,
            BigDecimal averageXgFor,
            BigDecimal averageXgAgainst,
            BigDecimal venueAverageGoalsFor,
            BigDecimal venueAverageGoalsAgainst) {
    }

    record MatchupInsight(
            String summary,
            boolean xgAvailable,
            List<MatchupSignal> signals) {
        public MatchupInsight {
            signals = signals == null ? List.of() : List.copyOf(signals);
        }
    }

    record MatchupSignal(
            String dimension,
            String edge,
            String evidence,
            String confidence) {
    }

    record PredictionInsight(
            String status,
            String predictedResult,
            BigDecimal homeWinProbability,
            BigDecimal drawProbability,
            BigDecimal awayWinProbability,
            BigDecimal confidence,
            String reasoning,
            Instant calculatedAt) {
    }

    record MarketInsight(
            BigDecimal homeOdds,
            BigDecimal drawOdds,
            BigDecimal awayOdds,
            BigDecimal homeImpliedProbability,
            BigDecimal drawImpliedProbability,
            BigDecimal awayImpliedProbability,
            BigDecimal overround) {
    }
}
