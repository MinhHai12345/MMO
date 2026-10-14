package com.mmo.module.fb.ingest.provider.model;

import java.math.BigDecimal;

public record ProviderOdds(
        String bookmaker,
        String market,
        String outcome,
        BigDecimal odd
) {
}
