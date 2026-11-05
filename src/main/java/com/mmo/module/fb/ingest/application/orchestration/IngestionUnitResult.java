package com.mmo.module.fb.ingest.application.orchestration;

import com.mmo.module.fb.ingest.enums.CheckpointStatus;

import java.util.Objects;

/** Immutable outcome for exactly one provider/dataset/league/season work unit. */
public record IngestionUnitResult(
        IngestionWorkUnit unit,
        CheckpointStatus status,
        int fetched,
        int persisted,
        int skippedRows,
        int failedRows,
        boolean skipped,
        long durationMillis,
        String message) {

    public IngestionUnitResult {
        Objects.requireNonNull(unit, "unit must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (fetched < 0 || persisted < 0 || skippedRows < 0 || failedRows < 0 || durationMillis < 0) {
            throw new IllegalArgumentException("Work-unit counters and duration must be non-negative");
        }
        if (skipped && status != CheckpointStatus.PENDING) {
            throw new IllegalArgumentException("A checkpoint-skipped unit must retain PENDING result status");
        }
    }

    public static IngestionUnitResult completed(IngestionWorkUnit unit, long durationMillis) {
        return new IngestionUnitResult(unit, CheckpointStatus.COMPLETED, 0, 0, 0, 0,
                false, durationMillis, null);
    }

    public static IngestionUnitResult skipped(IngestionWorkUnit unit) {
        return new IngestionUnitResult(unit, CheckpointStatus.PENDING, 0, 0, 0, 0,
                true, 0, "Already satisfied by a terminal historical checkpoint");
    }

    public static IngestionUnitResult notAvailable(IngestionWorkUnit unit, String message, long durationMillis) {
        return new IngestionUnitResult(unit, CheckpointStatus.NOT_AVAILABLE, 0, 0, 0, 0,
                false, durationMillis, message);
    }

    public static IngestionUnitResult partial(IngestionWorkUnit unit, String message, long durationMillis) {
        return new IngestionUnitResult(unit, CheckpointStatus.PARTIAL, 0, 0, 0, 1,
                false, durationMillis, message);
    }

    public static IngestionUnitResult failed(IngestionWorkUnit unit, String message, long durationMillis) {
        return new IngestionUnitResult(unit, CheckpointStatus.FAILED, 0, 0, 0, 1,
                false, durationMillis, message);
    }

    public boolean isSuccessful() {
        return skipped || status == CheckpointStatus.COMPLETED || status == CheckpointStatus.NOT_AVAILABLE;
    }

    public boolean isRetryable() {
        return !skipped && (status == CheckpointStatus.PARTIAL
                || status == CheckpointStatus.FAILED || status == CheckpointStatus.RUNNING);
    }
}
