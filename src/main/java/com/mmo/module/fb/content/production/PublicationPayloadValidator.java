package com.mmo.module.fb.content.production;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Conservative preflight checks for common public API payload limits.
 * Provider-specific validation must still run in the official platform adapter.
 */
public final class PublicationPayloadValidator {

    private PublicationPayloadValidator() {}

    public static void validate(
            PublicationPlatform platform,
            String title,
            String body,
            String mediaReference) {
        if (platform == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Publication platform is required");
        }
        if (title == null || title.isBlank() || body == null || body.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title and body are required");
        }

        int bodyLength = codePointLength(body);
        switch (platform) {
            case X -> requireAtMost(body.length(), 280, "X post body exceeds the conservative 280-character preflight limit");
            case TELEGRAM -> requireAtMost(bodyLength, 4096, "Telegram message exceeds 4096 characters");
            case FACEBOOK -> requireAtMost(bodyLength, 63206, "Facebook post body exceeds 63206 characters");
            case INSTAGRAM -> {
                requireAtMost(bodyLength, 2200, "Instagram caption exceeds 2200 characters");
                requireMedia(mediaReference, "Instagram publishing requires a media asset");
            }
            case TIKTOK -> {
                requireAtMost(bodyLength, 2200, "TikTok caption exceeds 2200 characters");
                requireMedia(mediaReference, "TikTok publishing requires a video asset");
            }
            case YOUTUBE -> {
                requireAtMost(title.length(), 100, "YouTube title exceeds 100 characters");
                requireAtMost(bodyLength, 5000, "YouTube description exceeds 5000 characters");
                requireMedia(mediaReference, "YouTube publishing requires a video asset");
            }
        }
    }

    private static int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }

    private static void requireAtMost(int actual, int maximum, String message) {
        if (actual > maximum) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private static void requireMedia(String mediaReference, String message) {
        if (mediaReference == null || mediaReference.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }
}
