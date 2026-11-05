package com.mmo.module.fb.domain.match.enums;

/**
 * Provider identity owned by the Match domain. Ingestion adapters translate their
 * configured provider enum by name at the module boundary.
 */
public enum MatchProvider {
    SOFA_SCORE,
    UNDER_STATS,
    API_FOOTBALL,
    FOOTBALL_DATA,
    FOOTBALL_DATA_UK;

    public static MatchProvider fromName(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            throw new IllegalArgumentException("Provider name must not be blank");
        }
        return valueOf(providerName.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
