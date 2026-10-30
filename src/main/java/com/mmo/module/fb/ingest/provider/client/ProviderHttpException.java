package com.mmo.module.fb.ingest.provider.client;

import org.springframework.http.HttpHeaders;

/** Provider-independent HTTP failure retaining status, headers and response payload. */
public class ProviderHttpException extends RuntimeException {
    private final int statusCode;
    private final HttpHeaders responseHeaders;
    private final String responseBody;

    public ProviderHttpException(int statusCode, HttpHeaders responseHeaders, String responseBody,
                                 String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.responseHeaders = responseHeaders == null ? HttpHeaders.EMPTY : HttpHeaders.readOnlyHttpHeaders(responseHeaders);
        this.responseBody = responseBody;
    }

    public int getStatusCode() { return statusCode; }
    public HttpHeaders getResponseHeaders() { return responseHeaders; }
    public String getResponseBody() { return responseBody; }
}
