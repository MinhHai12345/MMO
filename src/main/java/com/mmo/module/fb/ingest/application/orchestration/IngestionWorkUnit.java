package com.mmo.module.fb.ingest.application.orchestration;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.Dataset;

import java.util.Objects;

/**
 * One provider/dataset/league/season decision produced by DataSourcePolicy.
 * A secondary unit is a permitted fallback, never a second owner.
 */
public record IngestionWorkUnit(
        Dataset dataset,
        Long seasonId,
        Long leagueId,
        String season,
        Provider provider,
        Role role) {

    public IngestionWorkUnit {
        Objects.requireNonNull(dataset, "dataset must not be null");
        Objects.requireNonNull(seasonId, "seasonId must not be null");
        Objects.requireNonNull(leagueId, "leagueId must not be null");
        if (season == null || season.isBlank()) {
            throw new IllegalArgumentException("season must not be blank");
        }
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(role, "role must not be null");
    }

    public enum Role {
        PRIMARY,
        SECONDARY
    }
}
