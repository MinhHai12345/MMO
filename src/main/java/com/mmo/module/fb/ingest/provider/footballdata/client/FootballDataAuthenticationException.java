package com.mmo.module.fb.ingest.provider.footballdata.client;

/** Indicates that football-data.org rejected the configured credentials (HTTP 401). */
public class FootballDataAuthenticationException extends IllegalStateException {
    public FootballDataAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
