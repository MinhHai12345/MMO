package com.mmo.module.fb.ingest.provider.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Source-neutral match and xG values returned by a historical statistics provider. */
public record ProviderMatchStatistics(
        String externalMatchId,
        String homeTeamExternalId,
        String homeTeamName,
        String homeTeamShortName,
        String awayTeamExternalId,
        String awayTeamName,
        String awayTeamShortName,
        LocalDateTime kickoff,
        boolean result,
        Integer homeScore,
        Integer awayScore,
        BigDecimal homeXg,
        BigDecimal awayXg,
        BigDecimal homeWinForecast,
        BigDecimal drawForecast,
        BigDecimal awayWinForecast
) {
}
