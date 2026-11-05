package com.mmo.module.fb.ingest.application.exception;

/** Signals that a provider does not expose this dataset for the requested work unit. */
public class IngestionNotAvailableException extends RuntimeException {
    public IngestionNotAvailableException(String message) {
        super(message);
    }
}
