package com.mmo.module.fb.ingest.application.exception;

/** Provider-neutral network/transport failure surfaced to application orchestration. */
public class ProviderTransportException extends RuntimeException {
    public ProviderTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
