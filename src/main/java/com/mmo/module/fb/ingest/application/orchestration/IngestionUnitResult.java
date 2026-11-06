package com.mmo.module.fb.ingest.application.orchestration;

import com.mmo.module.fb.ingest.enums.CheckpointStatus;

import java.util.Objects;

/** Immutable outcome for exactly one provider/dataset/league/season work unit. */
public record IngestionUnitResult(
        IngestionWorkUnit unit,
        CheckpointStatus status,
        Integer fetched,
        Integer persisted,
        Integer skippedRows,
        Integer failedRows,
        boolean skipped,
        long durationMillis,
        String message,
        String failureCode,
        boolean retryable) {

    /** Backwards-compatible constructor for successful and generic outcomes. */
    public IngestionUnitResult(IngestionWorkUnit unit, CheckpointStatus status,
                               Integer fetched, Integer persisted, Integer skippedRows,
                               Integer failedRows, boolean skipped, long durationMillis,
                               String message) {
        this(unit, status, fetched, persisted, skippedRows, failedRows, skipped,
                durationMillis, message, null, false);
    }

    public IngestionUnitResult {
        Objects.requireNonNull(unit, "unit must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (negative(fetched) || negative(persisted) || negative(skippedRows)
                || negative(failedRows) || durationMillis < 0) {
            throw new IllegalArgumentException("Work-unit counters and duration must be non-negative");
        }
        if (skipped && status != CheckpointStatus.PENDING) {
            throw new IllegalArgumentException("A checkpoint-skipped unit must retain PENDING result status");
        }
        if (retryable && (status == CheckpointStatus.COMPLETED || status == CheckpointStatus.NOT_AVAILABLE
                || status == CheckpointStatus.PENDING)) {
            throw new IllegalArgumentException("Successful or skipped outcomes cannot be retryable failures");
        }
    }

    public static IngestionUnitResult completed(IngestionWorkUnit unit, long durationMillis) {
        return new IngestionUnitResult(unit, CheckpointStatus.COMPLETED, null, null, null, null,
                false, durationMillis, null, null, false);
    }

    public static IngestionUnitResult skipped(IngestionWorkUnit unit) {
        return skipped(unit, "Already satisfied by a terminal historical checkpoint");
    }

    public static IngestionUnitResult skipped(IngestionWorkUnit unit, String reason) {
        return new IngestionUnitResult(unit, CheckpointStatus.PENDING, null, null, null, null,
                true, 0, reason, null, false);
    }

    public static IngestionUnitResult notAvailable(IngestionWorkUnit unit, String message, long durationMillis) {
        return new IngestionUnitResult(unit, CheckpointStatus.NOT_AVAILABLE, null, null, null, null,
                false, durationMillis, message, "NOT_AVAILABLE", false);
    }

    public static IngestionUnitResult partial(IngestionWorkUnit unit, String message, long durationMillis) {
        return new IngestionUnitResult(unit, CheckpointStatus.PARTIAL, null, null, null, null,
                false, durationMillis, message, "PARTIAL_DATA", false);
    }

    public static IngestionUnitResult failed(IngestionWorkUnit unit, String message, long durationMillis) {
        return failed(unit, message, durationMillis, "INGESTION_FAILED", false);
    }

    public static IngestionUnitResult failed(IngestionWorkUnit unit, String message, long durationMillis,
                                             String failureCode, boolean retryable) {
        return new IngestionUnitResult(unit, CheckpointStatus.FAILED, null, null, null, null,
                false, durationMillis, message, failureCode, retryable);
    }

    private static boolean negative(Integer value) {
        return value != null && value < 0;
    }

    public boolean isSuccessful() {
        return skipped || status == CheckpointStatus.COMPLETED || status == CheckpointStatus.NOT_AVAILABLE;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
