package com.mmo.module.fb.ingest.application.exception;

/** Provider-neutral classified failure surfaced by outbound provider adapters. */
public class ProviderDataException extends RuntimeException {
    private final String failureCode;
    private final boolean notAvailable;
    private final boolean retryable;

    public ProviderDataException(String message, Throwable cause, boolean notAvailable, boolean retryable) {
        this(message, cause, null, notAvailable, retryable);
    }

    public ProviderDataException(String message, Throwable cause, String failureCode,
                                 boolean notAvailable, boolean retryable) {
        super(message, cause);
        this.failureCode = failureCode;
        this.notAvailable = notAvailable;
        this.retryable = retryable;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public boolean isNotAvailable() {
        return notAvailable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
