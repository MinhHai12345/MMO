package com.mmo.module.fb.ingest.provider.model;

import java.math.BigDecimal;

/** Canonical player match metrics mapped from a provider response. */
public record ProviderPlayerMatchStats(
        Integer minutesPlayed,
        Integer goals,
        Integer assists,
        Integer shots,
        Integer shotsOnTarget,
        BigDecimal xg,
        BigDecimal xa,
        Integer passes,
        BigDecimal passAccuracy,
        Integer keyPasses,
        Integer tackles,
        Integer interceptions,
        Integer duelsWon,
        Integer dribbles,
        Integer foulsDrawn,
        Integer foulsCommitted,
        Integer yellowCards,
        Integer redCards
) {}
