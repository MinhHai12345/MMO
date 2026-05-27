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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Slf4j
@Component
public abstract class AbstractCrawler implements CrawlerStrategy {
    @Resource
    protected Playwright playwright;

    protected Browser browser;
    private int usageCount = 0;
    private static final int MAX_USAGE_THRESHOLD = 150;
    private final Random random = new Random();

    // Cập nhật danh sách User-Agent thực tế và mới hơn
    private static final List<String> USER_AGENTS = Arrays.asList(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0"
    );

    protected synchronized void ensureBrowser() {
        if (browser == null || !browser.isConnected() || usageCount >= MAX_USAGE_THRESHOLD) {
            closeEverything();
            log.info("🔄 [{}] Khởi tạo/Làm mới instance Browser sạch...", getProvider().name());

            this.browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setArgs(Arrays.asList(
                            "--no-sandbox",
                            "--disable-dev-shm-usage",
                            "--disable-blink-features=AutomationControlled",
                            "--disable-infobars",
                            "--window-size=1920,1080",
                            "--disable-gpu"
                    )));
            this.usageCount = 0;
        }
    }

    @Override
    public synchronized Page createPage() {
        ensureBrowser();
        usageCount++;

        String selectedUserAgent = USER_AGENTS.get(random.nextInt(USER_AGENTS.size()));

        // Thiết lập các Header chuẩn của trình duyệt thật để bypass Cloudflare WAF
        Map<String, String> extraHeaders = new HashMap<>();
        extraHeaders.put("Accept", "*/*");
        extraHeaders.put("Accept-Language", "en-US,en;q=0.9,vi;q=0.8");
        extraHeaders.put("Referer", "https://www.sofascore.com/");
        extraHeaders.put("Origin", "https://www.sofascore.com");
        extraHeaders.put("Sec-Fetch-Dest", "empty");
        extraHeaders.put("Sec-Fetch-Mode", "cors");
        extraHeaders.put("Sec-Fetch-Site", "same-origin");

        // 🔥 Tối ưu: Tạo BrowserContext riêng biệt cho TỪNG Page.
        // Việc này giúp cách ly Cookie, Cache và giả lập hành vi thiết bị độc lập tuyệt đối.
        BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent(selectedUserAgent)
                .setViewportSize(1920, 1080)
                .setExtraHTTPHeaders(extraHeaders)
                .setLocale("en-US")
                .setTimezoneId("Asia/Ho_Chi_Minh"));

        Page page = context.newPage();

        // Thêm một lớp bảo mật Script chống các hàm kiểm tra Bot phổ biến
        page.addInitScript("delete Object.getPrototypeOf(navigator).webdriver;");

        return page;
    }

    protected void randomDelay() {
        try {
            // Tăng độ trễ ngẫu nhiên từ 1.5s -> 3.5s để đánh lừa thuật toán phát hiện tần suất
            long delay = 1500 + random.nextInt(2000);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Tiện ích giải phóng tài nguyên của một Page và Context đi kèm sau khi cào xong
     */
    protected void closePage(Page page) {
        if (page != null) {
            BrowserContext context = page.context();
            try {
                page.close();
                if (context != null) {
                    context.close();
                }
            } catch (Exception e) {
                log.error("Lỗi khi giải phóng Page/Context: {}", e.getMessage());
            }
        }
    }

    private void closeEverything() {
        try {
            if (browser != null) {
                browser.close();
                browser = null;
                log.info("🧹 Đã giải phóng hoàn toàn bộ nhớ Browser: {}", getProvider().name());
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
