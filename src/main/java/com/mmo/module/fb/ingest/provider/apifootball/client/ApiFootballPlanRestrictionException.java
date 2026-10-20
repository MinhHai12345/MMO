package com.mmo.module.fb.ingest.provider.apifootball.client;

/**
 * Signals that the configured API-Football subscription cannot access a requested resource/season.
 * This is a non-retryable limitation for the current subscription, not a transient provider failure.
 */
public class ApiFootballPlanRestrictionException extends IllegalStateException {

    public ApiFootballPlanRestrictionException(String message) {
        super(message);
    }
}
