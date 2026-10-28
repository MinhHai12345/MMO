package com.mmo.module.fb.ingest.provider.sofascore.client;

/**
 * Signals that SofaScore redirected an automated fetch to a CAPTCHA page.
 *
 * <p>This is not treated as a transient navigation failure: retrying the same
 * page repeatedly is unlikely to produce the requested API responses.</p>
 */
public class SofaScoreCaptchaException extends SofaScoreFetchException {

    public SofaScoreCaptchaException(String message) {
        super(message);
    }
}
