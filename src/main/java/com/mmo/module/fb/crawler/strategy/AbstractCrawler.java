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
    private final Random random = new Random();

    private static final List<String> USER_AGENTS = Arrays.asList(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0"
    );

    protected void ensureBrowser() {
        if (playwright == null || browser == null || !browser.isConnected() || usageCount.get() >= MAX_USAGE_THRESHOLD) {
            synchronized (this) {
                if (playwright == null || browser == null || !browser.isConnected() || usageCount.get() >= MAX_USAGE_THRESHOLD) {
                    if (browser != null) {
                        log.info("🔄 [{}] Đạt ngưỡng {} requests hoặc mất kết nối. Tách biệt browser cũ...", getProvider().name(), MAX_USAGE_THRESHOLD);
                        Browser oldBrowser = this.browser;
                        new Thread(() -> {
                            try {
                                Thread.sleep(30000);
                                if (oldBrowser.isConnected()) {
                                    oldBrowser.close();
                                    log.info("🧹 Đã dọn dẹp an toàn instance Browser cũ sau thời gian chờ hoãn.");
                                }
                            } catch (Exception e) {
                                log.error("Lỗi khi dọn dẹp browser cũ ngầm: {}", e.getMessage());
                            }
                        }).start();
                    }

                    if (playwright == null || !browser.isConnected()) {
                        closeEverythingInternal();
                    }
                    try {
                        log.info("🔄 [{}] Tiến hành khởi tạo lại hệ thống Driver và Browser sạch...", getProvider().name());
                        if (this.playwright == null) {
                            this.playwright = Playwright.create();
                        }
                        this.browser = this.playwright.chromium().launch(new BrowserType.LaunchOptions()
                                .setHeadless(true)
                                .setArgs(Arrays.asList(
                                        "--no-sandbox",
                                        "--disable-dev-shm-usage",
                                        "--disable-blink-features=AutomationControlled",
                                        "--disable-infobars",
                                        "--window-size=1920,1080",
                                        "--disable-gpu"
                                )));
                        usageCount.set(0);
                    } catch (Exception e) {
                        log.error("🚨 Thất bại nghiêm trọng khi tạo mới Playwright/Browser: {}", e.getMessage());
                        this.browser = null;
                        this.playwright = null;
                        throw e;
                    }
                }
            }
        }
    }

    @Override
    public synchronized Page createPage() {
        ensureBrowser();
        usageCount.incrementAndGet();
        String selectedUserAgent = USER_AGENTS.get(random.nextInt(USER_AGENTS.size()));
        Map<String, String> extraHeaders = new HashMap<>();
        extraHeaders.put("Accept", "*/*");
        extraHeaders.put("Accept-Language", "en-US,en;q=0.9,vi;q=0.8");
        extraHeaders.put("Referer", "https://www.sofascore.com/");
        extraHeaders.put("Origin", "https://www.sofascore.com");
        extraHeaders.put("Sec-Fetch-Dest", "empty");
        extraHeaders.put("Sec-Fetch-Mode", "cors");
        extraHeaders.put("Sec-Fetch-Site", "same-origin");

        BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent(selectedUserAgent)
                .setViewportSize(1920, 1080)
                .setExtraHTTPHeaders(extraHeaders)
                .setLocale("en-US")
                .setTimezoneId("Asia/Ho_Chi_Minh"));

        Page page = context.newPage();
        page.addInitScript(
                "Object.defineProperty(navigator, 'webdriver', {get: () => undefined});\n" +
                "Object.defineProperty(navigator, 'plugins', {get: () => [1, 2, 3, 4, 5]});\n" +
                "Object.defineProperty(navigator, 'languages', {get: () => ['en-US', 'en']});\n" +
                "window.chrome = { runtime: {} };"
        );

        return page;
    }

    /**
     * Tiện ích giải phóng tài nguyên của một Page và Context đi kèm sau khi cào xong
     */
    protected void closePage(Page page) {
        if (page != null) {
            try {
                BrowserContext context = page.context();
                page.close();
                if (context != null) {
                    context.close();
                }
            } catch (Exception e) {
                log.error("Lỗi khi giải phóng Page/Context: {}", e.getMessage());
            }
        }
    }

    private synchronized void closeEverythingInternal() {
        try {
            if (browser != null) {
                browser.close();
            }
        } finally {
            browser = null;
        }

        try {
            if (playwright != null) {
                playwright.close();
            }
        } finally {
            playwright = null;
        }
        log.info("🧹 Đã hủy bỏ hoàn toàn các thực thể kết nối cũ.");
    }

    @PreDestroy
    public void shutdown() {
        closeEverythingInternal();
    }


    /**
     * PIPELINE 2: Dành cho các hàm đơn giản không cần thực thể đầu vào (Ví dụ: storeLeagues)
     */
    protected <D> void executeSimpleStorePipeline(Function<Page, D> fetchFunction, Consumer<D> saveConsumer) {
        Page page = this.createPage();
        try {
            D dtoResult = fetchFunction.apply(page);
            if (dtoResult != null) {
                saveConsumer.accept(dtoResult);
            }
        } catch (PlaywrightException ex) {
            handleFatalConnectionError(ex);
        } catch (Exception ex) {
            log.error("❌ Lỗi xử lý trong Simple Pipeline: {}", ex.getMessage());
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

        Page page = this.createPage();
        try {
            for (E entity : entities) {
                if (page == null || page.isClosed()) {
                    log.info("🔄 [Pipeline] Page bị đóng bất ngờ. Đang tạo lại Page mới...");
                    page = this.createPage();
                }
                try {
                    D dtoResult = fetchFunction.apply(entity, page);
                    if (dtoResult != null) {
                        saveConsumer.accept(entity, dtoResult);
                    }
                } catch (PlaywrightException ex) {
                    boolean isFatal = handleFatalConnectionError(ex);
                    if (isFatal) {
                        page = null;
                    }
                } catch (Exception ex) {
                    log.error("❌ Lỗi xử lý phần tử trong Pipeline: {}", ex.getMessage());
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
        Page page = this.createPage();
        try {
            return fetchFunction.apply(page);
        } catch (PlaywrightException ex) {
            handleFatalConnectionError(ex);
        } catch (Exception ex) {
            log.error("❌ Lỗi xử lý trong Simple Fetch Pipeline: {}", ex.getMessage());
        } finally {
            this.closePage(page);
        }
        return null;
    }

    private boolean handleFatalConnectionError(PlaywrightException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "";
        if (msg.contains("Playwright connection closed") || msg.contains("Connection closed")) {
            log.error("🚨 [Fatal Connection] Phát hiện sập kết nối ống dẫn gốc! Đang xóa bỏ instance hỏng...");
            synchronized (this) {
                this.browser = null;
                this.playwright = null;
            }
            return true;
        }
        log.error("❌ Lỗi hệ thống Playwright: {}", msg);
        return false;
    }
}
