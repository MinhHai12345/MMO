package com.mmo.module.fb.ingest.adapter.out.provider.sofascore.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

@Slf4j
public abstract class AbstractSofaScoreClient {
    private static final int NAVIGATION_MAX_ATTEMPTS = 5;
    private static final long NAVIGATION_TIMEOUT_MS = 15_000;
    private static final long[] RETRY_DELAYS_MS = {5_000, 10_000, 20_000, 30_000};
    private static final long REQUIRED_ENDPOINT_TIMEOUT_SECONDS = 20;
    private static final long EVENT_PUMP_INTERVAL_MS = 100;

    protected final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Captures multiple independent API responses emitted by one page navigation.
     * Required endpoints gate completion; optional endpoints are collected opportunistically
     * during navigation/trigger actions and a short grace period.
     */
    protected DynamicFetchResult safeFetch(
            SofaScorePageSpec pageSpec,
            Page page,
            Consumer<Page> triggerAction) {
        if (pageSpec == null) {
            throw new IllegalArgumentException("SofaScore page specification is required");
        }
        String loadPageUrl = pageSpec.loadPageUrl();
        List<SofaScoreEndpointSpec> endpointSpecs = pageSpec.endpointSpecs();
        if (endpointSpecs == null || endpointSpecs.isEmpty()) {
            throw new IllegalArgumentException("At least one SofaScore endpoint must be configured");
        }

        Map<String, CompletableFuture<String>> futuresMap = new ConcurrentHashMap<>();
        for (SofaScoreEndpointSpec spec : endpointSpecs) {
            if (futuresMap.putIfAbsent(spec.endpoint(), new CompletableFuture<>()) != null) {
                throw new IllegalArgumentException("Duplicate SofaScore endpoint matcher: " + spec.endpoint());
            }
        }
        List<CompletableFuture<String>> requiredFutures = endpointSpecs.stream()
                .filter(SofaScoreEndpointSpec::required)
                .map(spec -> futuresMap.get(spec.endpoint()))
                .toList();
        if (requiredFutures.isEmpty()) {
            throw new IllegalArgumentException("At least one SofaScore endpoint must be required");
        }

        Consumer<Response> responseHandler = response -> {
            String responseUrl = response.url();
            for (SofaScoreEndpointSpec spec : endpointSpecs) {
                CompletableFuture<String> future = futuresMap.get(spec.endpoint());
                if (!future.isDone() && spec.matchesUrl(responseUrl)) {
                    try {
                        if (response.ok()) {
                            String body = response.text();
                            boolean captured = future.complete(body);
                            if (captured) {
                                log.info("[SOFA_SCORE][FETCH][CAPTURED] endpoint={}, status={}, bodyLength={}",
                                        spec.endpoint(), response.status(), body == null ? 0 : body.length());
                            }
                        } else if (spec.required()) {
                            SofaScoreFetchException failure = new SofaScoreFetchException(
                                    "SofaScore required endpoint returned HTTP " + response.status()
                                            + ": " + responseUrl);
                            future.completeExceptionally(failure);
                            log.error("[SOFA_SCORE][FETCH][HTTP_ERROR] endpoint={}, status={}, url={}",
                                    spec.endpoint(), response.status(), responseUrl);
                        }
                    } catch (Exception exception) {
                        future.completeExceptionally(exception);
                    }
                }
            }
        };

        page.onResponse(responseHandler);
        try {
            navigateWithRetry(loadPageUrl, page);
            failIfCaptchaPage(page, loadPageUrl);
            if (triggerAction != null) {
                try {
                    triggerAction.accept(page);
                } catch (Exception exception) {
                    log.warn("SofaScore trigger action failed for page {}: {}", loadPageUrl,
                            exception.getMessage());
                }
            }
            failIfCaptchaPage(page, loadPageUrl);

            CompletableFuture<Void> requiredCompletion =
                    CompletableFuture.allOf(requiredFutures.toArray(new CompletableFuture[0]));
            long deadline = System.nanoTime()
                    + TimeUnit.SECONDS.toNanos(REQUIRED_ENDPOINT_TIMEOUT_SECONDS);

            // Playwright's synchronous API dispatches browser events while Playwright calls
            // are being processed. Do not block on CompletableFuture.get(): that can prevent
            // page.onResponse callbacks for XHR/fetch requests from being dispatched.
            while (!requiredCompletion.isDone() && System.nanoTime() < deadline) {
                page.waitForTimeout(EVENT_PUMP_INTERVAL_MS);
            }
            if (!requiredCompletion.isDone()) {
                throw new TimeoutException("Required SofaScore endpoints were not captured before deadline");
            }
            requiredCompletion.join();

            boolean hasPendingOptional = endpointSpecs.stream()
                    .filter(spec -> !spec.required())
                    .anyMatch(spec -> !futuresMap.get(spec.endpoint()).isDone());
            if (hasPendingOptional) {
                page.waitForTimeout(2000);
            }
        } catch (TimeoutException exception) {
            throw new SofaScoreFetchException(
                    "Timed out after " + REQUIRED_ENDPOINT_TIMEOUT_SECONDS
                            + " seconds waiting for required SofaScore API endpoints: "
                            + missingRequiredEndpoints(endpointSpecs, futuresMap), exception);
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof SofaScoreFetchException fetchException) {
                throw fetchException;
            }
            throw new SofaScoreFetchException(
                    "Required SofaScore API endpoint failed for page " + loadPageUrl,
                    cause == null ? exception : cause);
        } catch (SofaScoreFetchException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SofaScoreFetchException(
                    "Unexpected failure while fetching SofaScore page " + loadPageUrl, exception);
        } finally {
            page.offResponse(responseHandler);
        }

        DynamicFetchResult fetchResult = new DynamicFetchResult();
        for (SofaScoreEndpointSpec spec : endpointSpecs) {
            CompletableFuture<String> future = futuresMap.get(spec.endpoint());
            if (!future.isDone()) {
                if (spec.required()) {
                    throw new SofaScoreFetchException(
                            "Required SofaScore API endpoint was not captured: " + spec.endpoint());
                }
                log.debug("Optional SofaScore API endpoint was not emitted by page {}: {}",
                        loadPageUrl, spec.endpoint());
                continue;
            }
            try {
                String json = future.join();
                if (json != null && !json.isBlank()) {
                    fetchResult.put(spec.endpoint(),
                            objectMapper.readValue(json, spec.responseType()));
                } else if (spec.required()) {
                    throw new SofaScoreFetchException(
                            "Required SofaScore API endpoint returned an empty body: " + spec.endpoint());
                }
            } catch (SofaScoreFetchException exception) {
                throw exception;
            } catch (Exception exception) {
                if (spec.required()) {
                    throw new SofaScoreFetchException(
                            "Could not parse required SofaScore endpoint " + spec.endpoint(), exception);
                }
                log.warn("Could not parse optional SofaScore endpoint {}: {}",
                        spec.endpoint(), exception.getMessage());
            }
        }
        return fetchResult;
    }

    private String missingRequiredEndpoints(
            List<SofaScoreEndpointSpec> endpointSpecs,
            Map<String, CompletableFuture<String>> futuresMap) {
        return endpointSpecs.stream()
                .filter(SofaScoreEndpointSpec::required)
                .filter(spec -> {
                    CompletableFuture<String> future = futuresMap.get(spec.endpoint());
                    return !future.isDone() || future.isCompletedExceptionally() || future.isCancelled();
                })
                .map(SofaScoreEndpointSpec::endpoint)
                .sorted()
                .toList()
                .toString();
    }

    private void failIfCaptchaPage(Page page, String requestedUrl) {
        String finalUrl = page.url();
        if (isCaptchaUrl(finalUrl)) {
            log.warn("SofaScore CAPTCHA detected. currentPageUrl={}", finalUrl);
            boolean resolved = waitForManualCaptchaResolution(page, requestedUrl);
            String resolvedUrl = page.url();
            if (resolved && !isCaptchaUrl(resolvedUrl)) {
                try {
                    // Re-open the original page with the same browser context/cookies so its API
                    // responses are captured by the listener already registered in safeFetch.
                    page.navigate(requestedUrl, new Page.NavigateOptions()
                            .setWaitUntil(WaitUntilState.COMMIT)
                            .setTimeout(NAVIGATION_TIMEOUT_MS));
                    String fetchUrl = page.url();
                    if (!isCaptchaUrl(fetchUrl)) {
                        log.info("Manual challenge completed; resumed normal SofaScore fetch in the same session. url={}",
                                fetchUrl);
                        return;
                    }
                } catch (PlaywrightException exception) {
                    log.warn("Could not resume the requested SofaScore page after manual intervention: {}",
                            exception.getMessage());
                }
            }
            throw new SofaScoreCaptchaException(
                    "SofaScore returned a CAPTCHA page instead of the requested page. requestedUrl="
                            + requestedUrl + ", finalUrl=" + resolvedUrl
                            + ". Automatic solving is not attempted; use manual intervention or an approved data API.");
        }
    }

    private boolean isCaptchaUrl(String url) {
        return url != null && url.toLowerCase(Locale.ROOT).contains("/captcha.html");
    }

    /**
     * Optional hook for an operator to solve a CAPTCHA manually in a visible browser.
     * Implementations must use a bounded wait and must not automate CAPTCHA solving.
     */
    protected boolean waitForManualCaptchaResolution(Page page, String requestedUrl) {
        return false;
    }

    private void navigateWithRetry(String loadPageUrl, Page page) {
        PlaywrightException lastFailure = null;
        for (int attempt = 1; attempt <= NAVIGATION_MAX_ATTEMPTS; attempt++) {
            try {
                page.navigate(loadPageUrl, new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.COMMIT)
                        .setTimeout(NAVIGATION_TIMEOUT_MS));
                return;
            } catch (PlaywrightException exception) {
                lastFailure = exception;
                boolean retryable = isTransientNavigationFailure(exception);
                log.warn("SofaScore navigation failed (attempt {}/{}): url={}, retryable={}, reason={}",
                        attempt, NAVIGATION_MAX_ATTEMPTS, loadPageUrl, retryable, exception.getMessage());

                if (!retryable || attempt == NAVIGATION_MAX_ATTEMPTS) {
                    break;
                }

                long delay = RETRY_DELAYS_MS[attempt - 1];
                log.info("Retrying SofaScore navigation in {} ms (attempt {}/{})",
                        delay, attempt + 1, NAVIGATION_MAX_ATTEMPTS);
                try {
                    page.waitForTimeout(delay);
                } catch (PlaywrightException waitException) {
                    throw new SofaScoreFetchException(
                            "Playwright page became unavailable while waiting to retry SofaScore navigation: "
                                    + loadPageUrl, waitException);
                }
            }
        }
        throw new SofaScoreFetchException(
                "Could not navigate to SofaScore page after navigation attempts: " + loadPageUrl
                        + ". Check network access, DNS, proxy/VPN, or site availability.",
                lastFailure);
    }

    private boolean isTransientNavigationFailure(PlaywrightException exception) {
        String message = exception.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);

        // A closed browser/page is a lifecycle failure, not a network retry.
        if (normalized.contains("target page, context or browser has been closed")
                || normalized.contains("browser has been closed")
                || normalized.contains("playwright connection closed")
                || normalized.contains("playwright was closed")) {
            return false;
        }

        return normalized.contains("err_connection_reset")
                || normalized.contains("err_connection_closed")
                || normalized.contains("err_connection_refused")
                || normalized.contains("err_timed_out")
                || normalized.contains("err_network_changed")
                || normalized.contains("navigation timeout")
                || normalized.contains("timeout ");
    }
}
