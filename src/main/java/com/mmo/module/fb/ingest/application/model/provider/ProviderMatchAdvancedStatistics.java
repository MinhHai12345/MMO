package com.mmo.module.fb.ingest.application.model.provider;

import java.math.BigDecimal;
import java.util.Map;

/** Provider-neutral advanced match statistics, keyed by canonical statistic names. */
public record ProviderMatchAdvancedStatistics(
        String externalMatchId,
        Map<String, BigDecimal> home,
        Map<String, BigDecimal> away) {

    public ProviderMatchAdvancedStatistics {
        home = home == null ? Map.of() : Map.copyOf(home);
        away = away == null ? Map.of() : Map.copyOf(away);
    }
}
