package com.mmo.module.fb.ingest.provider.sofascore.client;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.mmo.module.fb.ingest.enums.Provider;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import com.mmo.AppProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

@Slf4j
@Component
public abstract class AbstractSofaScoreProvider {
    @Autowired
    private AppProperties appProperties;

    protected volatile Playwright playwright;
    protected volatile Browser browser;
    private final AtomicInteger usageCount = new AtomicInteger(0);
    private static final int MAX_USAGE_THRESHOLD = 150;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private final AtomicBoolean needsRecycle = new AtomicBoolean(false);

    private final Map<String, BrowserContext> activeContexts = new ConcurrentHashMap<>();


    protected final <T> T withPage(Function<Page, T> action) {
        Page page = null;
        try {
            page = createPage();
            return action.apply(page);
        } finally {
            closePage(page);
        }
    }

    protected synchronized void ensureBrowser() {
        boolean isDisconnected = (browser == null || !browser.isConnected());
        boolean shouldRecycle = (needsRecycle.get() || usageCount.get() >= MAX_USAGE_THRESHOLD);

        if (playwright == null || isDisconnected || (shouldRecycle && activeContexts.isEmpty())) {
            log.info("🔄 [{}] Initializing/recycling Browser (Reason: Disconnected={}, ShouldRecycle={}, ActiveContexts={})...",
                    Provider.SOFA_SCORE.name(), isDisconnected, shouldRecycle, activeContexts.size());
            closeEverythingInternal();
            try {
                this.playwright = Playwright.create();
                boolean manualCaptchaEnabled = isManualCaptchaInterventionEnabled();
                boolean headless = !manualCaptchaEnabled && isHeadlessConfigured();
                this.browser = this.playwright.chromium().launch(new BrowserType.LaunchOptions()
                        .setHeadless(headless)
                        .setArgs(List.of(
                                "--no-sandbox",
                                "--disable-dev-shm-usage",
                                "--disable-gpu",
                                "--window-size=1920,1080"
                        )));
                log.info("[{}] Playwright Chromium started: version={}, headless={}, manualIntervention={}",
                        Provider.SOFA_SCORE.name(), browser.version(), headless, manualCaptchaEnabled);
                this.usageCount.set(0);
                this.needsRecycle.set(false);
                log.info("✅ [{}] Browser initialization successful!", Provider.SOFA_SCORE.name());
            } catch (Exception e) {
                log.error("🚨 Critical failure creating Playwright/Browser: {}", e.getMessage(), e);
                closeEverythingInternal();
                throw e;
            }
        } else if (shouldRecycle) {
            needsRecycle.set(true);
        }
    }

    private boolean isManualCaptchaInterventionEnabled() {
        AppProperties.ProviderConfig config = sofaScoreConfig();
        return Boolean.parseBoolean(config.getOption("manual-captcha-intervention"));
    }

    private boolean isHeadlessConfigured() {
        AppProperties.ProviderConfig config = sofaScoreConfig();
        String value = config.getOption("headless");
        return value == null || value.isBlank() || Boolean.parseBoolean(value);
    }

    private AppProperties.ProviderConfig sofaScoreConfig() {
        AppProperties.ProviderConfig config =
                appProperties.getIngestion().getProviderConfig().get(Provider.SOFA_SCORE);
        if (config == null || !config.isEnabled()) {
            throw new IllegalStateException("SofaScore provider is disabled or not configured");
        }
        return config;
    }

    public Page createPage() {
        synchronized (this) {
            ensureBrowser();
            usageCount.incrementAndGet();
        }

        BrowserContext context = null;
        String contextId = null;
        try {
            synchronized (this) {
                // Let Chromium provide a consistent User-Agent and client hints.
                // Manually mixing browser versions/platform headers can create an invalid profile.
                context = browser.newContext(new Browser.NewContextOptions()
                        .setViewportSize(1920, 1080)
                        .setLocale("en-US")
                        .setTimezoneId("Asia/Ho_Chi_Minh"));
            }

            contextId = Integer.toHexString(System.identityHashCode(context));
            activeContexts.put(contextId, context);

            Page page = context.newPage();
            page.onRequestFailed(request -> {
                String failure = request.failure() == null ? "unknown" : request.failure();
                String url = request.url();
                boolean cloudflareChallenge = url != null
                        && url.contains("challenges.cloudflare.com/cdn-cgi/challenge-platform/");
                if (cloudflareChallenge && "net::ERR_ABORTED".equalsIgnoreCase(failure)) {
                    log.debug("[{}] Cloudflare challenge subrequest aborted during navigation: method={}, url={}",
                            Provider.SOFA_SCORE.name(), request.method(), url);
                    return;
                }
                log.warn("[{}] Browser request failed: method={}, url={}, failure={}",
                        Provider.SOFA_SCORE.name(), request.method(), url, failure);
            });
            page.onResponse(response -> {
                if (response.status() >= 400) {
                    log.warn("[{}] Browser response error: status={}, url={}",
                            Provider.SOFA_SCORE.name(), response.status(), response.url());
                }
            });
            return page;
        } catch (Exception e) {
            if (contextId != null) {
                activeContexts.remove(contextId);
            }
            if (context != null) {
                try {
                    context.close();
                } catch (Exception ignored) {
                }
            }
            log.error("🚨 Failed to initialize Page/Context: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Tiện ích giải phóng tài nguyên của một Page và Context đi kèm sau khi cào xong
     */
    protected void closePage(Page page) {
        if (page != null) {
            try {
                BrowserContext context = page.context();
                if (!page.isClosed()) {
                    page.close();
                }
                if (context != null) {
                    String contextId = Integer.toHexString(System.identityHashCode(context));
                    activeContexts.remove(contextId);
                    context.close();
                }
            } catch (Exception e) {
                log.warn("⚠️ Minor error while disposing Page/Context: {}", e.getMessage());
            } finally {
                if (needsRecycle.get() && activeContexts.isEmpty()) {
                    synchronized (this) {
                        ensureBrowser();
                    }
                }
            }
        }
    }

    private synchronized void closeEverythingInternal() {
        Map<String, BrowserContext> contextsToClose = new HashMap<>(activeContexts);
        activeContexts.clear();

        contextsToClose.forEach((id, ctx) -> {
            try {
                ctx.close();
            } catch (Exception ignored) {
            }
        });

        if (browser != null) {
            try {
                if (browser.isConnected()) {
                    browser.close();
                }
            } catch (Exception e) {
                log.warn("Error closing browser: {}", e.getMessage());
            } finally {
                browser = null;
            }
        }

        if (playwright != null) {
            try {
                playwright.close();
            } catch (Exception e) {
                log.warn("Error closing Playwright instance: {}", e.getMessage());
            } finally {
                playwright = null;
            }
        }
        log.info("🧹 Successfully disposed all active instances and connections.");
    }

    @PreDestroy
    public void shutdown() {
        closeEverythingInternal();
    }


    /**
     * PIPELINE 2: Dành cho các hàm đơn giản không cần thực thể đầu vào (Ví dụ: storeLeagues)
     */
    protected <D> void executeSimpleStorePipeline(Function<Page, D> fetchFunction, Consumer<D> saveConsumer) {
        Page page = null;
        try {
            page = this.createPage();
            D dtoResult = fetchFunction.apply(page);
            if (dtoResult != null) {
                saveConsumer.accept(dtoResult);
            }
        } catch (SofaScoreFetchException ex) {
            throw ex;
        } catch (PlaywrightException ex) {
            handleFatalConnectionError(ex);
        } catch (Exception ex) {
            log.error("❌ Processing error in Simple Pipeline: {}", ex.getMessage(), ex);
        } finally {
            this.closePage(page);
        }
    }

    /**
     * PIPELINE 1: Dành cho các hàm xử lý tuần tự theo danh sách đầu vào (Seasons, Teams, Matches, Statistics...)
     */
    protected <E, D> void executeStorePipeline(List<E> entities, BiFunction<E, Page, D> fetchFunction,
                                               BiConsumer<E, D> saveConsumer) {
        if (CollectionUtils.isEmpty(entities)) {
            return;
        }

        Page page = null;
        try {
            page = this.createPage();
            for (E entity : entities) {
                int attempts = 0;
                boolean success = false;
                while (attempts < MAX_RETRY_ATTEMPTS && !success) {
                    attempts++;
                    if (page == null || page.isClosed()) {
                        log.info("🔄 [Pipeline] Page unavailable. Re-initializing new Page...");
                        page = this.createPage();
                    }
                    try {
                        D dtoResult = fetchFunction.apply(entity, page);
                        if (dtoResult != null) {
                            saveConsumer.accept(entity, dtoResult);
                        }
                        success = true;
                    } catch (PlaywrightException ex) {
                        boolean isFatal = handleFatalConnectionError(ex);
                        this.closePage(page);
                        page = null;
                        if (!isFatal && attempts < MAX_RETRY_ATTEMPTS) {
                            log.warn("⚠️ Retrying item [{}] (Attempt {}/{})", entity, attempts, MAX_RETRY_ATTEMPTS);
                            backoffDelay(attempts);
                        }
                    } catch (SofaScoreFetchException ex) {
                        throw ex;
                    } catch (Exception ex) {
                        log.error("❌ Error processing item [{}] in Pipeline: {}", entity, ex.getMessage());
                        break;
                    }
                }
            }
        } finally {
            this.closePage(page);
        }
    }

    /**
     * PIPELINE 3 (Rút gọn): Chỉ cào dữ liệu từ Page, tự động quản lý đóng/thả Page và return kết quả DTO.
     */
    protected <D> D executeSimpleFetchPipeline(Function<Page, D> fetchFunction) {
        Page page = null;
        try {
            page = this.createPage();
            return fetchFunction.apply(page);
        } catch (SofaScoreFetchException ex) {
            throw ex;
        } catch (PlaywrightException ex) {
            handleFatalConnectionError(ex);
        } catch (Exception ex) {
            log.error("❌ Processing error in Simple Fetch Pipeline: {}", ex.getMessage(), ex);
        } finally {
            this.closePage(page);
        }
        return null;
    }

    private boolean handleFatalConnectionError(PlaywrightException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "";
        if (msg.contains("Playwright connection closed")
            || msg.contains("Connection closed")
            || msg.contains("Target page, context or browser has been closed")) {
            log.error("🚨 [Fatal Connection] Detected broken pipe! Resetting browser instance...");
            synchronized (this) {
                closeEverythingInternal();
            }
            return true;
        }
        log.error("❌ Playwright system error: {}", msg);
        return false;
    }

    private void backoffDelay(int attempt) {
        try {
            Thread.sleep(1000L * attempt);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
