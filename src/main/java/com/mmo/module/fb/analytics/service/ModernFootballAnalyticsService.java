package com.mmo.module.fb.analytics.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface ModernFootballAnalyticsService {

    ModernMatchReport analyze(Long matchId);

    record ModernMatchReport(
            Long matchId,
            Instant kickoff,
            String competition,
            String round,
            String status,
            TeamMetrics home,
            TeamMetrics away,
            MatchComparison comparison,
            List<PlayerImpact> playerImpacts,
            PostMatchReview postMatchReview,
            ContentBrief contentBrief,
            List<String> dataLimitations) {
        public ModernMatchReport {
            playerImpacts = playerImpacts == null ? List.of() : List.copyOf(playerImpacts);
            dataLimitations = dataLimitations == null ? List.of() : List.copyOf(dataLimitations);
        }
    }

    record TeamMetrics(
            Long teamId,
            String teamName,
            BigDecimal possession,
            BigDecimal ppda,
            BigDecimal fieldTilt,
            BigDecimal xgChain,
            BigDecimal xgBuildup,
            BigDecimal setPieceXg,
            BigDecimal counterAttackXg,
            Integer deepCompletions,
            Integer finalThirdEntries,
            Integer shotsFromBox,
            Integer shotsOutsideBox,
            BigDecimal goals,
            BigDecimal xg,
            BigDecimal xgMinusGoals,
            Integer shots,
            Integer shotsOnTarget,
            Integer corners,
            Integer yellowCards,
            Integer redCards) {
    }

    record MatchComparison(
            String tacticalSummary,
            List<String> evidence) {
        public MatchComparison {
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
        }
    }

    record PlayerImpact(
            Long playerId,
            String playerName,
            String position,
            Integer minutesPlayed,
            Integer goals,
            Integer assists,
            BigDecimal xg,
            BigDecimal xa,
            Integer shotsOnTarget,
            Integer keyPasses,
            Integer tackles,
            Integer interceptions,
            Integer duelsWon,
            Integer yellowCards,
            Integer redCards,
            BigDecimal contributionScore) {
    }

    record PostMatchReview(
            Integer homeScore,
            Integer awayScore,
            BigDecimal homeXg,
            BigDecimal awayXg,
            BigDecimal homeXgOverperformance,
            BigDecimal awayXgOverperformance,
            String resultSummary) {
    }

    record ContentBrief(
            String headlineAngle,
            String matchNarrative,
            List<String> evidenceBackedHooks,
            List<String> suggestedFormats,
            String editorialCaution) {
        public ContentBrief {
            evidenceBackedHooks = evidenceBackedHooks == null ? List.of() : List.copyOf(evidenceBackedHooks);
            suggestedFormats = suggestedFormats == null ? List.of() : List.copyOf(suggestedFormats);
        }
    }
}
