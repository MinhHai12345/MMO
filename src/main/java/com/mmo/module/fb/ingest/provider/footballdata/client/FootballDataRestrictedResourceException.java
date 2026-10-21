package com.mmo.module.fb.ingest.provider.footballdata.client;

/** Indicates that the configured football-data.org subscription cannot access a resource. */
public class FootballDataRestrictedResourceException extends IllegalStateException {
    public FootballDataRestrictedResourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
