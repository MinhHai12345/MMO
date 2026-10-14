package com.mmo.module.fb.ingest.provider.model;

public record ProviderTeam(
        String externalId,
        String name,
        String shortName,
        String logoUrl,
        Integer foundedYear,
        String stadiumName,
        Integer stadiumCapacity
) {
}
