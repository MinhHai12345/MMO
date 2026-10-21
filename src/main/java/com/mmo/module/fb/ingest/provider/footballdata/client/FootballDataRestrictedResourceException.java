package com.mmo.module.fb.ingest.provider.footballdata.client;

/**
 * Indicates that football-data.org recognizes an endpoint but the configured subscription
 * does not grant access to that resource (HTTP 403).
 */
public class FootballDataRestrictedResourceException extends IllegalStateException {
    public FootballDataRestrictedResourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
