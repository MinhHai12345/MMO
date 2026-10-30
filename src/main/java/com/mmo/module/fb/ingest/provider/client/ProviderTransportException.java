package com.mmo.module.fb.ingest.provider.client;

/** Network-level failure shared by REST provider clients. */
public class ProviderTransportException extends RuntimeException {
    public ProviderTransportException(String message, Throwable cause) { super(message, cause); }
}
