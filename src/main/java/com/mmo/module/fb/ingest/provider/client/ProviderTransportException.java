package com.mmo.module.fb.ingest.provider.client;

/**
 * @deprecated Use the provider-neutral application exception in orchestration code.
 * Provider clients may keep throwing this compatibility subtype.
 */
@Deprecated(forRemoval = true)
public class ProviderTransportException
        extends com.mmo.module.fb.ingest.application.exception.ProviderTransportException {
    public ProviderTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
