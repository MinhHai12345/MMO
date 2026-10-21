package com.mmo.module.fb.ingest.provider.footballdata.client;

/** Non-retryable HTTP response from football-data.org, with its status code preserved. */
public class FootballDataRequestException extends IllegalStateException {
    private final int statusCode;

    public FootballDataRequestException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
