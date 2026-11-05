package com.mmo.module.fb.ingest.provider.model;

import com.mmo.module.fb.domain.league.enums.StandingType;

import java.util.ArrayList;
import java.util.List;

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
        groupName = groupName == null || groupName.isBlank() ? null : groupName.trim();
    }

    /**
     * Checks only invariants that can be proven from fields supplied by the provider.
     * Missing optional statistics remain missing; they are never converted to zero.
     */
    public List<String> consistencyIssues() {
        List<String> issues = new ArrayList<>();
        if (teamExternalId == null || teamExternalId.isBlank()) {
            issues.add("teamExternalId is missing");
        }
        if (position != null && position < 1) issues.add("position must be positive");
        if (points != null && points < 0) issues.add("points must not be negative");

        if (played != null && won != null && drawn != null && lost != null) {
            if (played < 0 || won < 0 || drawn < 0 || lost < 0) {
                issues.add("played/won/drawn/lost must not be negative");
            } else if (played != won + drawn + lost) {
                issues.add("played must equal won + drawn + lost");
            }
        }
        if (goalsFor != null && goalsAgainst != null && goalDifference != null) {
            if (goalsFor < 0 || goalsAgainst < 0) {
                issues.add("goalsFor/goalsAgainst must not be negative");
            } else if (goalDifference != goalsFor - goalsAgainst) {
                issues.add("goalDifference must equal goalsFor - goalsAgainst");
            }
        }
        return List.copyOf(issues);
    }
}
