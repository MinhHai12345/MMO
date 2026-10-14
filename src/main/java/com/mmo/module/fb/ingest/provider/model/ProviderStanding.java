package com.mmo.module.fb.ingest.provider.model;

public record ProviderStanding(
        String teamExternalId,
        Integer position,
        Integer points,
        Integer goalDifference,
        String form,
        String groupName,
        Integer played,
        Integer won,
        Integer drawn,
        Integer lost,
        Integer goalsFor,
        Integer goalsAgainst
) {
}
