package com.mmo.module.fb.ingest.provider.model;

import java.math.BigDecimal;

/** Monthly league baseline derived only from completed matches in the selected month. */
public record ProviderLeagueMonthlyStats(
        String leagueExternalId,
        int year,
        int month,
        int matches,
        BigDecimal homeGoalsAverage,
        BigDecimal awayGoalsAverage,
        BigDecimal homeXgAverage,
        BigDecimal awayXgAverage
) {
}
