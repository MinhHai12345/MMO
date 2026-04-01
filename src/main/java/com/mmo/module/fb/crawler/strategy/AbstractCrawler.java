package com.mmo.module.fb.crawler.strategy;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

@Slf4j
@Component
public abstract class AbstractCrawler implements CrawlerStrategy {
    @Resource
    protected Playwright playwright;

    protected Browser browser;
    protected BrowserContext browserContext;
    private int usageCount = 0;
    private static final int MAX_USAGE_THRESHOLD = 250;
    private final Random random = new Random();

    private static final List<String> USER_AGENTS = Arrays.asList(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    );

    protected synchronized void ensureBrowser() {
        if (browser == null || !browser.isConnected() || usageCount >= MAX_USAGE_THRESHOLD) {
            closeEverything();
            log.info("🔄 [{}] Đang khởi tạo/làm mới Browser riêng biệt...", getProvider().name());

            this.browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setArgs(Arrays.asList(
                            "--no-sandbox",
                            "--disable-dev-shm-usage",
                            "--disable-gpu",
                            "--blink-settings=imagesEnabled=false"
                    )));
            this.browserContext = browser.newContext(new Browser.NewContextOptions()
                    .setUserAgent(USER_AGENTS.get(random.nextInt(USER_AGENTS.size()))));
            this.usageCount = 0;
        }
    }

    @Override
    public synchronized Page createPage() {
        ensureBrowser();
        usageCount++;
        return browserContext.newPage();
    }

    protected void randomDelay() {
        try {
            long delay = 1000 + random.nextInt(2000);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void closeEverything() {
        try {
            if (browserContext != null) {
                browserContext.close();
                browserContext = null;
            }
            if (browser != null) {
                browser.close();
                log.info("🧹 Đã đóng Browser của Strategy: {}", getProvider().name());
            }
        } catch (Exception e) {
            log.error("Lỗi khi đóng Browser: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        closeEverything();
    }
}
