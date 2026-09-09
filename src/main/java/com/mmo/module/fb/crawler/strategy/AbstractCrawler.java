package com.mmo.module.fb.crawler.strategy;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

@Slf4j
@Component
public abstract class AbstractCrawler implements CrawlerStrategy {

    protected volatile Playwright playwright;
    protected volatile Browser browser;
    private final AtomicInteger usageCount = new AtomicInteger(0);
    private static final int MAX_USAGE_THRESHOLD = 150;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private final Random random = new Random();

    private final Map<String, BrowserContext> activeContexts = new ConcurrentHashMap<>();

    private static final List<String> USER_AGENTS = Arrays.asList(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0"
    );

    protected synchronized void ensureBrowser() {
        boolean isDisconnected = (browser == null || !browser.isConnected());
        boolean isExceededThreshold = (usageCount.get() >= MAX_USAGE_THRESHOLD);

        if (playwright == null || isDisconnected || isExceededThreshold) {
            log.info("🔄 [{}] Initializing/recycling Browser (Reason: Disconnected={}, ExceededThreshold={})...",
                    getProvider().name(), isDisconnected, isExceededThreshold);
            closeEverythingInternal();
            try {
                this.playwright = Playwright.create();
                this.browser = this.playwright.chromium().launch(new BrowserType.LaunchOptions()
                        .setHeadless(true)
                        .setArgs(List.of(
                                "--disable-blink-features=AutomationControlled",
                                "--disable-infobars",
                                "--no-sandbox",
                                "--disable-dev-shm-usage",
                                "--disable-gpu",
                                "--disable-setuid-sandbox",
                                "--window-size=1920,1080"
                        )));
                this.usageCount.set(0);
                log.info("✅ [{}] Browser initialization successful!", getProvider().name());
            } catch (Exception e) {
                log.error("🚨 Critical failure creating Playwright/Browser: {}", e.getMessage(), e);
                closeEverythingInternal();
                throw e;
            }
        }
    }

    @Override
    public Page createPage() {
        ensureBrowser();
        usageCount.incrementAndGet();

        String selectedUserAgent = USER_AGENTS.get(random.nextInt(USER_AGENTS.size()));
        Map<String, String> baseHeaders = new HashMap<>();
        baseHeaders.put("Accept-Language", "en-US,en;q=0.9");
        baseHeaders.put("sec-ch-ua", "\"Chromium\";v=\"124\", \"Google Chrome\";v=\"124\", \"Not-A.Brand\";v=\"99\"");
        baseHeaders.put("sec-ch-ua-mobile", "?0");
        baseHeaders.put("sec-ch-ua-platform", "\"Windows\"");

        BrowserContext context = null;
        String contextId = null;
        try {
            synchronized (this) {
                context = browser.newContext(new Browser.NewContextOptions()
                        .setUserAgent(selectedUserAgent)
                        .setViewportSize(1920, 1080)
                        .setExtraHTTPHeaders(baseHeaders)
                        .setLocale("en-US")
                        .setTimezoneId("Asia/Ho_Chi_Minh"));
            }

            contextId = Integer.toHexString(System.identityHashCode(context));
            activeContexts.put(contextId, context);

            Page page = context.newPage();

            // Optimized anti-detection script injection
            page.addInitScript("""
                        (() => {
                            const newProto = Navigator.prototype;
                            delete newProto.webdriver;
                            Object.defineProperty(newProto, 'webdriver', {get: () => false});
                            Object.defineProperty(navigator, 'languages', {get: () => ['en-US', 'en']});
                            Object.defineProperty(navigator, 'plugins', {get: () => [1, 2, 3, 4, 5]});
                            window.chrome = { runtime: {}, loadTimes: function() {}, csi: function() {} };
                        })();
                    """);

            return page;
        } catch (Exception e) {
            if (context != null) {
                if (contextId != null) {
                    activeContexts.remove(contextId);
                }
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
                        }
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
}
