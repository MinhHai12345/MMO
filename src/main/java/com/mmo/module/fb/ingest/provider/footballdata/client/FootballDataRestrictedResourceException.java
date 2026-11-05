package com.mmo.module.fb.ingest.provider.footballdata.client;

import com.mmo.module.fb.ingest.application.exception.ProviderAccessException;

/** Provider-neutral classification of a resource unavailable to the current subscription. */
public class FootballDataRestrictedResourceException extends ProviderAccessException {
    public FootballDataRestrictedResourceException(String message, Throwable cause) {
        super(Kind.RESOURCE_RESTRICTED, message, cause);
    }
}
