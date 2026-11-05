package com.mmo.module.fb.ingest.enums;

/** Durable state of one provider/dataset/league/season ingestion unit. */
public enum CheckpointStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    PARTIAL,
    FAILED,
    NOT_AVAILABLE
}
