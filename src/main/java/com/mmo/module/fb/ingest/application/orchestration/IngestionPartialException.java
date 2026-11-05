package com.mmo.module.fb.ingest.application.orchestration;

/** Signals that a provider stage persisted some work but stopped before full coverage. */
public class IngestionPartialException extends RuntimeException {
    public IngestionPartialException(String message) {
        super(message);
    }
}
