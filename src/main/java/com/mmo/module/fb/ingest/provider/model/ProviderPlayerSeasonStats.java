package com.mmo.module.fb.ingest.provider.model;

import java.math.BigDecimal;

/** Provider-neutral season aggregate statistics for one player. */
public record ProviderPlayerSeasonStats(
        String externalPlayerId,
        String playerName,
        String teamName,
        String position,
        Integer appearances,
        Integer minutesPlayed,
        Integer goals,
        BigDecimal xg,
        Integer assists,
        BigDecimal xa,
        Integer shots,
        Integer keyPasses,
        Integer yellowCards,
        Integer redCards,
        Integer nonPenaltyGoals,
        BigDecimal npxg,
        BigDecimal xgChain,
        BigDecimal xgBuildup
) {}
