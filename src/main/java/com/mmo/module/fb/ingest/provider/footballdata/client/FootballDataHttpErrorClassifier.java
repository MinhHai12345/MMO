package com.mmo.module.fb.ingest.provider.footballdata.client;

/** Converts HTTP response codes into typed exceptions shared by all football-data resources. */
public class FootballDataHttpErrorClassifier {

    public RuntimeException classify(int statusCode, String message, Throwable cause) {
        return switch (statusCode) {
            case 401 -> new FootballDataAuthenticationException(
                    message + ". Check FOOTBALL_DATA_API_KEY.", cause);
            case 403 -> new FootballDataRestrictedResourceException(message, cause);
            case 429 -> new FootballDataRateLimitException(message, cause);
            default -> new FootballDataRequestException(statusCode, message, cause);
        };
    }
}
