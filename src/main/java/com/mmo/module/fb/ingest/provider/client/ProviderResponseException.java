package com.mmo.module.fb.ingest.provider.client;

/** A successful transport that did not produce a usable provider response. */
public class ProviderResponseException extends RuntimeException {
    private final String requestUri;
    private final String responseBody;

    public ProviderResponseException(String requestUri, String responseBody, String message, Throwable cause) {
        super(message, cause);
        this.requestUri = requestUri;
        this.responseBody = responseBody;
    }

    public String getRequestUri() {
        return requestUri;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
