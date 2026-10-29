package com.mmo.module.fb.ingest.provider.model;

import java.util.List;

/** Provider league identity plus the provider-specific key used in fetch requests. */
public record ProviderLeague(
        String externalId,
        String identify,
        String name,
        String country,
        String logoUrl,
        List<ProviderSeason> seasons
) {
}
