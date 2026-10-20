package com.mmo.module.fb.ingest.provider.footballdata.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmo.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
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
    private static final Pattern WAIT_SECONDS_PATTERN =
            Pattern.compile("(?i)wait\\s+(\\d+)\\s+seconds?");

    /**
     * Shared cooldown for this singleton client. Synchronization prevents concurrent ingestion
     * jobs from immediately consuming the quota again after a 429 response.
     */
    private long nextAllowedRequestAtMillis;

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    public synchronized <T> T get(String path, Class<T> responseType, Object... uriVariables) {
        AppProperties.FootballData properties = appProperties.getFootballData();
        if (properties == null || !properties.isEnabled()) {
            throw new IllegalStateException("football-data.org integration is disabled. Set FOOTBALL_DATA_ENABLED=true.");
        }
        if (!StringUtils.hasText(properties.getApiKey())
                || PLACEHOLDER_API_KEY.equalsIgnoreCase(properties.getApiKey().trim())) {
            throw new IllegalStateException("football-data.org API token is not configured. Set FOOTBALL_DATA_API_KEY.");
        }
        if (!StringUtils.hasText(properties.getBaseUrl())) {
            throw new IllegalStateException("football-data.org base URL is missing.");
        }

        awaitRateLimitCooldown();

        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                String json = webClientBuilder.clone()
                        .baseUrl(properties.getBaseUrl())
                        .defaultHeader("X-Auth-Token", properties.getApiKey())
                        .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                        // Fixture lists can exceed WebClient's default 256 KiB codec buffer.
                        .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE_BYTES))
                        .build().get().uri(path, uriVariables)
                        .retrieve().bodyToMono(String.class).block(REQUEST_TIMEOUT);
                if (!StringUtils.hasText(json)) {
                    throw new IllegalStateException("Empty response from football-data.org endpoint " + path);
                }
                return objectMapper.readValue(json, responseType);
            } catch (WebClientResponseException ex) {
                if (ex.getStatusCode().value() == 429) {
                    long waitSeconds = determineRetryDelaySeconds(ex);
                    nextAllowedRequestAtMillis = System.currentTimeMillis() + waitSeconds * 1_000L;
                    if (attempt == 0) {
                        // Retry once after the provider's stated cooldown. If it still fails,
                        // propagate a clear error instead of retrying indefinitely.
                        awaitRateLimitCooldown();
                        continue;
                    }
                    throw new IllegalStateException("football-data.org rate limit (HTTP 429) for "
                            + path + ". Retry after approximately " + waitSeconds
                            + " seconds. Response: " + ex.getResponseBodyAsString(), ex);
                }

                throw new IllegalStateException("football-data.org HTTP " + ex.getStatusCode().value()
                        + " for " + path + ": " + ex.getResponseBodyAsString(), ex);
            } catch (Exception ex) {
                if (ex instanceof IllegalStateException stateException) throw stateException;
                throw new IllegalStateException("Failed to call football-data.org endpoint " + path, ex);
            }
        }

        throw new IllegalStateException("football-data.org request failed after rate-limit retry for " + path);
    }

    private long determineRetryDelaySeconds(WebClientResponseException ex) {
        String retryAfter = ex.getHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        if (StringUtils.hasText(retryAfter)) {
            try {
                return Math.max(1, Long.parseLong(retryAfter.trim()));
            } catch (NumberFormatException ignored) {
                // Retry-After may also be an HTTP date; fall back to the provider response body.
            }
        }

        Matcher matcher = WAIT_SECONDS_PATTERN.matcher(ex.getResponseBodyAsString());
        if (matcher.find()) {
            try {
                return Math.max(1, Long.parseLong(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                // Use the conservative default below if the value is malformed or overflows.
            }
        }
        return DEFAULT_RATE_LIMIT_WAIT_SECONDS;
    }

    private void awaitRateLimitCooldown() {
        long remainingMillis = nextAllowedRequestAtMillis - System.currentTimeMillis();
        if (remainingMillis <= 0) return;

        try {
            Thread.sleep(remainingMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for football-data.org rate limit cooldown", ex);
        }
    }
}
