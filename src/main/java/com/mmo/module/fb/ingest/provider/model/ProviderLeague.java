package com.mmo.module.fb.ingest.provider.model;

import java.util.List;

public record ProviderLeague(
        String externalId,
        String name,
        String country,
        String logoUrl,
        List<ProviderSeason> seasons
) {
}
