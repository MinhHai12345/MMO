package com.mmo.module.fb.ingest.adapter.out.provider.footballdata.client;

import com.mmo.module.fb.ingest.application.exception.ProviderAccessException;

/** Provider-neutral classification of a provider quota/rate-limit response. */
public class FootballDataRateLimitException extends ProviderAccessException {
    public FootballDataRateLimitException(String message, Throwable cause) {
        super(Kind.RATE_LIMITED, message, cause);
    }
}
