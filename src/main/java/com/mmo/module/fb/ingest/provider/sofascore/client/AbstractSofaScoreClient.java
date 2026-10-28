package com.mmo.module.fb.ingest.provider.sofascore.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.WaitUntilState;
import com.mmo.module.fb.ingest.provider.model.DynamicFetchResult;
import lombok.extern.slf4j.Slf4j;
import org.thymeleaf.util.MapUtils;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

@Slf4j
public abstract class AbstractSofaScoreClient {
    private static final int NAVIGATION_MAX_ATTEMPTS = 5;
    private static final long NAVIGATION_TIMEOUT_MS = 15_000;
    private static final long[] RETRY_DELAYS_MS = {5_000, 10_000, 20_000, 30_000};

    protected final ObjectMapper objectMapper = new ObjectMapper();

    protected DynamicFetchResult safeFetch(
            String loadPageUrl,
            Map<String, Class<?>> endpointClassMap,
            Page page,
            Consumer<Page> triggerAction) {
        DynamicFetchResult fetchResult = new DynamicFetchResult();
        if (MapUtils.isEmpty(endpointClassMap)) {
            return fetchResult;
        }

        Map<String, CompletableFuture<String>> futuresMap = new ConcurrentHashMap<>();
        endpointClassMap.keySet().forEach(endpoint -> futuresMap.put(endpoint, new CompletableFuture<>()));

        Consumer<Response> responseHandler = response -> {
            try {
                if (response.ok()) {
                    String currentUrl = response.url();
                    for (String endpoint : endpointClassMap.keySet()) {
                        CompletableFuture<String> future = futuresMap.get(endpoint);
                        if (currentUrl.contains(endpoint) && !future.isDone()) {
                            future.complete(response.text());
                        }
                    }
                }
            } catch (Exception exception) {
                log.debug("Could not read intercepted SofaScore response: {}", exception.getMessage());
            }
        };

        page.onResponse(responseHandler);
        try {
            navigateWithRetry(loadPageUrl, page);
            failIfCaptchaPage(page, loadPageUrl);
            page.waitForTimeout(7000);
            if (triggerAction != null) {
                page.waitForTimeout(8000);
                try {
                    triggerAction.accept(page);
                    page.waitForTimeout(10000);
                } catch (Exception exception) {
                    log.warn("SofaScore trigger action failed: {}", exception.getMessage());
                }
            }
            // Cloudflare may redirect after the initial navigation check.
            failIfCaptchaPage(page, loadPageUrl);
            CompletableFuture<?>[] futuresArray = futuresMap.values().toArray(new CompletableFuture[0]);
            CompletableFuture.allOf(futuresArray).get(20, TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            log.warn("Timed out waiting for all intercepted SofaScore endpoints; parsing available responses");
        } catch (SofaScoreFetchException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SofaScoreFetchException(
                    "Unexpected failure while fetching SofaScore page " + loadPageUrl, exception);
        } finally {
            page.offResponse(responseHandler);
        }

        futuresMap.forEach((endpoint, future) -> {
            if (future.isDone() && !future.isCompletedExceptionally()) {
                try {
                    String json = future.getNow(null);
                    if (json != null && !json.isEmpty()) {
                        fetchResult.put(endpoint, objectMapper.readValue(json, endpointClassMap.get(endpoint)));
                    }
                } catch (Exception exception) {
                    throw new SofaScoreFetchException("Could not parse SofaScore endpoint " + endpoint, exception);
                }
            } else {
                log.warn("SofaScore endpoint was not captured: {}", endpoint);
            }
        });
        return fetchResult;
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
