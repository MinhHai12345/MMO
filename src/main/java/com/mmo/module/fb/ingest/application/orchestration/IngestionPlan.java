package com.mmo.module.fb.ingest.application.orchestration;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.Dataset;

import java.util.List;
import java.util.Objects;

/** Immutable season-aware ingestion plan. */
public record IngestionPlan(List<IngestionWorkUnit> units) {
    public IngestionPlan {
        units = units == null ? List.of() : List.copyOf(units);
    }

    public List<IngestionWorkUnit> units(Dataset dataset, IngestionWorkUnit.Role role) {
        return units.stream()
                .filter(unit -> unit.dataset() == dataset && unit.role() == role)
                .toList();
    }

    public List<IngestionWorkUnit> units(Dataset dataset, Provider provider,
                                         IngestionWorkUnit.Role role) {
        Objects.requireNonNull(provider, "provider must not be null");
        return units.stream()
                .filter(unit -> unit.dataset() == dataset
                        && unit.provider() == provider
                        && unit.role() == role)
                .toList();
    }

    public boolean has(Dataset dataset, Provider provider, IngestionWorkUnit.Role role) {
        return units.stream().anyMatch(unit -> unit.dataset() == dataset
                && unit.provider() == provider && unit.role() == role);
    }
}
