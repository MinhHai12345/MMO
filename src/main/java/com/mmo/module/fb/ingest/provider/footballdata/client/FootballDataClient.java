package com.mmo.module.fb.ingest.provider.footballdata.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.client.AbstractProviderClient;
import com.mmo.module.fb.ingest.provider.client.ProviderHttpClient;
import com.mmo.module.fb.ingest.provider.client.ProviderHttpException;
import com.mmo.module.fb.ingest.provider.client.ProviderTransportException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** football-data.org client retaining its provider-specific quota, retry and error policies. */
@Slf4j
@Component
public class FootballDataClient extends AbstractProviderClient {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final int MAX_IN_MEMORY_SIZE_BYTES = 10 * 1024 * 1024;
    private static final String PLACEHOLDER_API_KEY = "mock";
    private static final long DEFAULT_RATE_LIMIT_WAIT_SECONDS = 60;
    private static final long MAX_RATE_LIMIT_WAIT_SECONDS = 3_600;
    private static final long MIN_REQUEST_INTERVAL_MILLIS = 6_000;
    private static final Pattern WAIT_SECONDS_PATTERN =
            Pattern.compile("(?i)wait\\s+(\\d+)\\s+seconds?");
    private static final DateTimeFormatter HTTP_DATE_FORMAT = DateTimeFormatter.RFC_1123_DATE_TIME;

    private long nextAllowedRequestAtMillis;
    private final FootballDataRetryPolicy retryPolicy = new FootballDataRetryPolicy();
    private final FootballDataHttpErrorClassifier errorClassifier = new FootballDataHttpErrorClassifier();

    public FootballDataClient(ProviderHttpClient httpClient, AppProperties appProperties) {
        super(appProperties, httpClient);
    }

    public synchronized <T> T get(String path, Class<T> responseType, Object... uriVariables) {
        AppProperties.ProviderConfig config = providerConfig(Provider.FOOTBALL_DATA);
        if (!StringUtils.hasText(config.getApiKey())
                || PLACEHOLDER_API_KEY.equalsIgnoreCase(config.getApiKey().trim())) {
            throw new IllegalStateException("football-data.org API token is not configured. Set FOOTBALL_DATA_API_KEY.");
        }

        String expandedPath = expandUri(path, uriVariables);
        Map<String, String> headers = Map.of(
                "X-Auth-Token", config.getApiKey(),
                HttpHeaders.ACCEPT, "application/json");

        for (int attempt = 0; attempt < FootballDataRetryPolicy.MAX_ATTEMPTS; attempt++) {
            awaitRequestSlot();
            try {
                return exchange(Provider.FOOTBALL_DATA, HttpMethod.GET, expandedPath, headers,
                        Map.of(), null, typeOf(responseType), REQUEST_TIMEOUT, MAX_IN_MEMORY_SIZE_BYTES);
            } catch (ProviderHttpException ex) {
                int status = ex.getStatusCode();
                String message = "football-data.org HTTP " + status + " for " + path + ": " + ex.getResponseBody();
                if (status == 401 || status == 403) {
                    throw errorClassifier.classify(status, message, ex);
                }
                if (status == 429) {
                    long waitSeconds = determineRetryDelaySeconds(ex);
                    nextAllowedRequestAtMillis = Math.max(nextAllowedRequestAtMillis,
                            System.currentTimeMillis() + waitSeconds * 1_000L);
                    if (retryPolicy.shouldRetryStatus(status, attempt)) {
                        log.warn("football-data.org rate limited request; retrying once after {} seconds: {}",
                                waitSeconds, path);
                        continue;
                    }
                    throw errorClassifier.classify(status,
                            message + ". Retry after approximately " + waitSeconds + " seconds.", ex);
                }
                if (retryPolicy.shouldRetryStatus(status, attempt)) {
                    sleepBeforeRetry(retryPolicy.backoffMillis(attempt), path);
                    continue;
                }
                throw errorClassifier.classify(status, message, ex);
            } catch (ProviderTransportException ex) {
                if (retryPolicy.shouldRetryTransportFailure(attempt)) {
                    sleepBeforeRetry(retryPolicy.backoffMillis(attempt), path);
                    continue;
                }
                throw new IllegalStateException("Network failure calling football-data.org endpoint "
                        + path + " after " + (attempt + 1) + " attempts.", ex);
            }
        }
        throw new IllegalStateException("football-data.org request exhausted retry attempts for " + path);
    }

    static String expandUri(String path, Object... uriVariables) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(path);
        if (uriVariables == null || uriVariables.length == 0) {
            return builder.build().encode().toUriString();
        }
        return builder.buildAndExpand(uriVariables).encode().toUriString();
    }

    private long determineRetryDelaySeconds(ProviderHttpException ex) {
        String retryAfter = ex.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        if (StringUtils.hasText(retryAfter)) {
            String value = retryAfter.trim();
            try {
                return boundedWaitSeconds(Long.parseLong(value));
            } catch (NumberFormatException ignored) {
                try {
                    Instant retryAt = ZonedDateTime.parse(value, HTTP_DATE_FORMAT).toInstant();
                    return boundedWaitSeconds(Duration.between(Instant.now(), retryAt).getSeconds());
                } catch (DateTimeParseException ignoredDate) {
                    // Fall through to the provider response body.
                }
            }
        }

        Matcher matcher = WAIT_SECONDS_PATTERN.matcher(ex.getResponseBody() == null ? "" : ex.getResponseBody());
        if (matcher.find()) {
            try {
                return boundedWaitSeconds(Long.parseLong(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                // Keep conservative default.
            }
        }
        return DEFAULT_RATE_LIMIT_WAIT_SECONDS;
    }

    private long boundedWaitSeconds(long seconds) {
        return Math.min(MAX_RATE_LIMIT_WAIT_SECONDS, Math.max(1, seconds));
    }

    private void sleepBeforeRetry(long delayMillis, String path) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted before retrying football-data.org endpoint " + path, ex);
        }
    }

    private void awaitRequestSlot() {
        long remainingMillis = nextAllowedRequestAtMillis - System.currentTimeMillis();
        if (remainingMillis > 0) {
            try {
                Thread.sleep(remainingMillis);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for football-data.org request slot", ex);
            }
        }
        nextAllowedRequestAtMillis = System.currentTimeMillis() + MIN_REQUEST_INTERVAL_MILLIS;
    }

    private static <T> TypeReference<T> typeOf(Class<T> responseType) {
        return new TypeReference<>() {
            @Override public Type getType() { return responseType; }
        };
    }
}
