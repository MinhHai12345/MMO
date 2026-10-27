package com.mmo.module.fb.ingest.provider.footballdata.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmo.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class FootballDataClient {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(25);
    private static final int MAX_IN_MEMORY_SIZE_BYTES = 10 * 1024 * 1024;
    private static final String PLACEHOLDER_API_KEY = "mock";
    private static final long DEFAULT_RATE_LIMIT_WAIT_SECONDS = 60;
    /** Conservative pacing for football-data.org free-tier quotas (about 10 requests/minute). */
    private static final long MIN_REQUEST_INTERVAL_MILLIS = 6_000;
    private static final Pattern WAIT_SECONDS_PATTERN =
            Pattern.compile("(?i)wait\\s+(\\d+)\\s+seconds?");

    /** Shared cooldown prevents concurrent ingestion jobs from immediately consuming quota again. */
    private long nextAllowedRequestAtMillis;

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;
    private final FootballDataRetryPolicy retryPolicy = new FootballDataRetryPolicy();
    private final FootballDataHttpErrorClassifier errorClassifier = new FootballDataHttpErrorClassifier();

    public synchronized <T> T get(String path, Class<T> responseType, Object... uriVariables) {
        AppProperties.ProviderConfig properties = appProperties.getIngestion().getProviderConfig().get(com.mmo.module.fb.ingest.enums.Provider.FOOTBALL_DATA);
        if (properties == null || !properties.isEnabled()) {
            throw new IllegalStateException("football-data.org integration is disabled. Set FOOTBALL_DATA_ENABLED=true.");
        }
        if (!StringUtils.hasText(properties.getApiKey())
                || PLACEHOLDER_API_KEY.equalsIgnoreCase(properties.getApiKey().trim())) {
            throw new IllegalStateException("football-data.org API token is not configured. Set FOOTBALL_DATA_API_KEY.");
        }
        if (!StringUtils.hasText(properties.getUri())) {
            throw new IllegalStateException("football-data.org base URL is missing.");
        }

        for (int attempt = 0; attempt < FootballDataRetryPolicy.MAX_ATTEMPTS; attempt++) {
            awaitRequestSlot();
            try {
                String json = webClientBuilder.clone()
                        .baseUrl(properties.getUri())
                        .defaultHeader("X-Auth-Token", properties.getApiKey())
                        .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                        .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE_BYTES))
                        .build().get().uri(path, uriVariables)
                        .retrieve().bodyToMono(String.class).block(REQUEST_TIMEOUT);
                if (!StringUtils.hasText(json)) {
                    throw new IllegalStateException("Empty response from football-data.org endpoint " + path);
                }
                return objectMapper.readValue(json, responseType);
            } catch (WebClientResponseException ex) {
                int status = ex.getStatusCode().value();
                String message = "football-data.org HTTP " + status + " for " + path
                        + ": " + ex.getResponseBodyAsString();

                if (status == 401 || status == 403) {
                    throw errorClassifier.classify(status, message, ex);
                }
                if (status == 429) {
                    long waitSeconds = determineRetryDelaySeconds(ex);
                    nextAllowedRequestAtMillis = Math.max(nextAllowedRequestAtMillis,
                            System.currentTimeMillis() + waitSeconds * 1_000L);
                    if (retryPolicy.shouldRetryStatus(status, attempt)) {
                        continue;
                    }
                    throw errorClassifier.classify(status, message
                            + ". Retry after approximately " + waitSeconds + " seconds.", ex);
                }
                if (retryPolicy.shouldRetryStatus(status, attempt)) {
                    sleepBeforeRetry(retryPolicy.backoffMillis(attempt), path);
                    continue;
                }
                throw errorClassifier.classify(status, message, ex);
            } catch (WebClientRequestException ex) {
                if (retryPolicy.shouldRetryTransportFailure(attempt)) {
                    sleepBeforeRetry(retryPolicy.backoffMillis(attempt), path);
                    continue;
                }
                throw new IllegalStateException(
                        "Network failure calling football-data.org endpoint " + path
                                + " after " + (attempt + 1) + " attempts.", ex);
            } catch (Exception ex) {
                if (ex instanceof IllegalStateException stateException) throw stateException;
                throw new IllegalStateException("Failed to call football-data.org endpoint " + path, ex);
            }
        }

        throw new IllegalStateException("football-data.org request exhausted retry attempts for " + path);
    }

    private long determineRetryDelaySeconds(WebClientResponseException ex) {
        String retryAfter = ex.getHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        if (StringUtils.hasText(retryAfter)) {
            try {
                return Math.max(1, Long.parseLong(retryAfter.trim()));
            } catch (NumberFormatException ignored) {
                // Retry-After may be an HTTP date; fall back to the provider response body.
            }
        }
        Matcher matcher = WAIT_SECONDS_PATTERN.matcher(ex.getResponseBodyAsString());
        if (matcher.find()) {
            try {
                return Math.max(1, Long.parseLong(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                // Use the conservative default below for malformed or overflowing values.
            }
        }
        return DEFAULT_RATE_LIMIT_WAIT_SECONDS;
    }

    private void sleepBeforeRetry(long delayMillis, String path) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted before retrying football-data.org endpoint " + path, ex);
        }
    }

    private void awaitRequestSlot() {
        long remainingMillis = nextAllowedRequestAtMillis - System.currentTimeMillis();
        if (remainingMillis > 0) {
            try {
                Thread.sleep(remainingMillis);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "Interrupted while waiting for football-data.org request slot", ex);
            }
        }
        nextAllowedRequestAtMillis = System.currentTimeMillis() + MIN_REQUEST_INTERVAL_MILLIS;
    }
}
