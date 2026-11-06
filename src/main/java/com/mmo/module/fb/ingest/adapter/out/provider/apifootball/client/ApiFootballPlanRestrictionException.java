package com.mmo.module.fb.ingest.adapter.out.provider.apifootball.client;

import com.mmo.module.fb.ingest.application.exception.ProviderAccessException;

/** Non-retryable limitation of the configured API-Football subscription. */
public class ApiFootballPlanRestrictionException extends ProviderAccessException {
    public ApiFootballPlanRestrictionException(String message) {
        super(Kind.PLAN_RESTRICTED, message, null);
    }
}
