package com.mmo.module.fb.analytics.service;

import java.math.BigDecimal;
import java.util.List;

public interface PlayerAnalyticsService {

    PlayerMatchInsight analyzeMatch(Long matchId);

    record PlayerMatchInsight(
            Long matchId,
            int playersWithStats,
            List<PlayerPerformance> standoutPlayers,
            List<String> dataWarnings) {
        public PlayerMatchInsight {
            standoutPlayers = standoutPlayers == null ? List.of() : List.copyOf(standoutPlayers);
            dataWarnings = dataWarnings == null ? List.of() : List.copyOf(dataWarnings);
        }
    }

    record PlayerPerformance(
            Long playerId,
            String playerName,
            String teamName,
            Integer minutesPlayed,
            Integer goals,
            Integer assists,
            BigDecimal xg,
            BigDecimal xa,
            Integer shots,
            Integer shotsOnTarget,
            Integer keyPasses,
            Integer tackles,
            Integer interceptions,
            Integer duelsWon,
            Integer yellowCards,
            Integer redCards,
            BigDecimal goalContribution,
            BigDecimal xgOverperformance,
            BigDecimal impactScore,
            List<String> evidence) {
        public PlayerPerformance {
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
        }
    }
}
