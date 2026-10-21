package com.mmo.module.fb.ingest.provider.footballdata.client;

/**
 * Signals that football-data.org rejected a request because the API quota was exceeded.
 */
public class FootballDataRateLimitException extends IllegalStateException {

    public FootballDataRateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
