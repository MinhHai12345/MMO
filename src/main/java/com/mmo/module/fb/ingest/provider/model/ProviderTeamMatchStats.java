package com.mmo.module.fb.ingest.provider.model;

import java.math.BigDecimal;

/**
 * Team statistics for one historical match. Match ID may be absent in the source's team-history
 * payload; callers should then correlate by team, date, venue and score rather than guessing.
 */
public record ProviderTeamMatchStats(
        String externalTeamId,
        String externalMatchId,
        String homeAway,
        String date,
        BigDecimal xg,
        BigDecimal xga,
        BigDecimal npxg,
        BigDecimal npxga,
        Integer ppdaAtt,
        Integer ppdaDef,
        Integer ppdaAllowedAtt,
        Integer ppdaAllowedDef,
        Integer deep,
        Integer deepAllowed,
        Integer scored,
        Integer missed,
        BigDecimal expectedPoints,
        String result,
        Integer wins,
        Integer draws,
        Integer losses,
        Integer points,
        BigDecimal npxGoalDifference
) {
}
