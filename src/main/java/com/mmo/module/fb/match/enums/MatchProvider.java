package com.mmo.module.fb.match.enums;

/**
 * Provider identity used by the Match domain. This deliberately does not depend on the
 * ingestion module; adapters translate their configured provider enum at the boundary.
 */
public enum MatchProvider {
    SOFA_SCORE,
    UNDER_STATS,
    API_FOOTBALL,
    FOOTBALL_DATA,
    FOOTBALL_DATA_UK;

    public static MatchProvider from(com.mmo.module.fb.ingest.enums.Provider provider) {
        return provider == null ? null : valueOf(provider.name());
    }
}
