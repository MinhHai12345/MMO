package com.mmo.module.fb.ingest.application.exception;

/** Provider-neutral classified failure surfaced by outbound provider adapters. */
public class ProviderDataException extends RuntimeException {
    private final boolean notAvailable;
    private final boolean retryable;

    public ProviderDataException(String message, Throwable cause, boolean notAvailable, boolean retryable) {
        super(message, cause);
        this.notAvailable = notAvailable;
        this.retryable = retryable;
    }

    public boolean isNotAvailable() {
        return notAvailable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
