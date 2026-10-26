package com.mmo.module.fb.ingest.service;

import com.mmo.module.fb.ingest.enums.Provider;

/**
 * Optional per-resource provider overrides for one bootstrap execution.
 * A null value delegates to the default routing in AppProperties.
 */
public record ProviderOverrides(
        Provider league,
        Provider team,
        Provider fixture,
        Provider standings) {

    public static ProviderOverrides defaults() {
        return new ProviderOverrides(null, null, null, null);
    }
}
