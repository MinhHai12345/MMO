package com.mmo.module.fb.ingest.provider.sofascore;

/**
 * Signals that a required SofaScore endpoint did not return a parseable response.
 * This is deliberately distinct from a successful response containing an empty list,
 * so an interrupted bootstrap is retried rather than recorded as complete.
 */
public class SofaScoreFetchException extends RuntimeException {

    public SofaScoreFetchException(String message) {
        super(message);
    }

    public SofaScoreFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
