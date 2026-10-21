package com.mmo.module.fb.ingest.provider.model;

import com.mmo.module.fb.domain.enums.StandingType;

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
        Integer goalsAgainst,
        StandingType standingType
) {
    /** Backward-compatible constructor for providers that only return the overall table. */
    public ProviderStanding(String teamExternalId, Integer position, Integer points,
                            Integer goalDifference, String form, String groupName,
                            Integer played, Integer won, Integer drawn, Integer lost,
                            Integer goalsFor, Integer goalsAgainst) {
        this(teamExternalId, position, points, goalDifference, form, groupName,
                played, won, drawn, lost, goalsFor, goalsAgainst, StandingType.TOTAL);
    }

    public ProviderStanding {
        standingType = standingType == null ? StandingType.TOTAL : standingType;
    }
}
