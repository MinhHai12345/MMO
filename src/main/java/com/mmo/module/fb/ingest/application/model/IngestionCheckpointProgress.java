package com.mmo.module.fb.ingest.application.model;

import java.time.Instant;

/** Durable resume cursor and cumulative row counters for one ingestion work unit. */
public record IngestionCheckpointProgress(
        String cursor,
        long processedRows,
        long persistedRows,
        long failedRows,
        Instant updatedAt) {

    public IngestionCheckpointProgress {
        if (processedRows < 0 || persistedRows < 0 || failedRows < 0) {
            throw new IllegalArgumentException("Checkpoint progress counters must be non-negative");
        }
    }
}
