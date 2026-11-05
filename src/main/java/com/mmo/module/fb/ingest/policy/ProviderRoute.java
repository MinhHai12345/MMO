package com.mmo.module.fb.ingest.policy;

import com.mmo.module.fb.ingest.enums.Provider;

import java.util.List;

/** Primary owner and explicitly permitted secondary contributors for one dataset/season. */
public record ProviderRoute(Provider primary, List<Provider> secondary) {
    public ProviderRoute {
        secondary = secondary == null ? List.of() : List.copyOf(secondary);
    }

    public boolean allows(Provider provider) {
        return provider != null && (provider == primary || secondary.contains(provider));
    }
}
