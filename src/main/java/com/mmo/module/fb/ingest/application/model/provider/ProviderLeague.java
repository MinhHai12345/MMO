package com.mmo.module.fb.ingest.application.model.provider;

import java.util.List;

public record ProviderLeague(
        String externalId,
        String identify,
        String name,
        String country,
        String logoUrl,
        List<ProviderSeason> seasons
) {
}
