package com.mmo.module.fb.ingest.application.model;

import java.time.Instant;
import java.util.List;

/** Result of a read-only diagnostic against the SofaScore provider. */
public record SofaScoreDiagnosticResult(
        String provider,
        String status,
        Instant checkedAt,
        List<StageResult> stages
) {
    public SofaScoreDiagnosticResult {
        stages = stages == null ? List.of() : List.copyOf(stages);
    }

    public record StageResult(
            String name,
            String status,
            long durationMs,
            Integer itemCount,
            String message
    ) {
    }
}
