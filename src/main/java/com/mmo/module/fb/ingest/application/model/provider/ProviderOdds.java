package com.mmo.module.fb.ingest.application.model.provider;

import java.math.BigDecimal;

public record ProviderOdds(
        String bookmaker,
        String market,
        String outcome,
        BigDecimal odd
) {
}
