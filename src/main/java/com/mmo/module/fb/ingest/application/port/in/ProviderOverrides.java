package com.mmo.module.fb.ingest.application.port.in;

import com.mmo.module.fb.ingest.enums.Provider;

/** Optional resource-level provider overrides for one bootstrap execution. */
public record ProviderOverrides(
        Provider league,
        Provider season,
        Provider team,
        Provider fixture,
        Provider standings) {

    public ProviderOverrides(Provider league, Provider team, Provider fixture, Provider standings) {
        this(league, null, team, fixture, standings);
    }

    public static ProviderOverrides defaults() {
        return new ProviderOverrides(null, null, null, null, null);
    }
}
