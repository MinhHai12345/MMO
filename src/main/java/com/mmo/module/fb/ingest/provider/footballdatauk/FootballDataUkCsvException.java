package com.mmo.module.fb.ingest.provider.footballdatauk;

/** Classified transport/content failure from Football-Data.co.uk. */
public class FootballDataUkCsvException extends RuntimeException {
    public enum Reason {
        NOT_AVAILABLE,
        RATE_LIMITED,
        SERVER_ERROR,
        NETWORK_ERROR,
        INVALID_CSV,
        HTTP_ERROR,
        UNSUPPORTED_URL
    }

    private final Reason reason;
    private final boolean retryable;

    public FootballDataUkCsvException(Reason reason, String message) {
        this(reason, message, null, false);
    }

    public FootballDataUkCsvException(Reason reason, String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.reason = reason;
        this.retryable = retryable;
    }

    public Reason getReason() {
        return reason;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public boolean isNotAvailable() {
        return reason == Reason.NOT_AVAILABLE;
    }
}
