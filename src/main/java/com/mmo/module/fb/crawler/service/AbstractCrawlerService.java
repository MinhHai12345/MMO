package com.mmo.module.fb.crawler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public abstract class AbstractCrawlerService {
    private final Random random = new Random();
    protected final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicInteger requestCounter = new AtomicInteger(0);


    /**
     * Hàm Generic xử lý cào dữ liệu an toàn, tự động chống block.
     * * @param url        Đường dẫn API cần gọi
     *
     * @param page  Đối tượng Playwright Page
     * @param clazz Class Mock Data để Jackson parse JSON (T.class)
     * @param <T>   Kiểu dữ liệu trả về mong muốn
     * @return Đối tượng đã được parse, hoặc null nếu lỗi
     */
    protected <T> T safeFetch(String url, Page page, Class<T> clazz) {
        try {
            handleAntiBotDelay(page);
            page.navigate("https://www.sofascore.com/");
            page.waitForTimeout(2000);
            Response response = page.navigate(url);

            if (response == null) {
                log.warn("⚠️ Response trả về null khi gọi URL: {}", url);
                return null;
            }
            int status = response.status();
            if (status == 200) {
                return objectMapper.readValue(response.text(), clazz);
            }
            if (status == 403 || status == 429) {
                log.warn("🚨 [Anti-Bot] Bị phát hiện với mã lỗi {} tại URL: {}. Tiến hành reset session...", status, url);
                if (page.context() != null) {
                    page.context().clearCookies();
                }
                Thread.sleep(60000);
            } else {
                log.warn("❌ Lỗi hệ thống HTTP {}: tại URL {}", status, url);
            }

        } catch (Exception e) {
            log.error("❌ Lỗi nghiêm trọng khi fetch data từ URL [{}]: {}", url, e.getMessage());

        }
        return null;
    }

    /**
     * Logic delay kết hợp Micro-break tách riêng để code sạch sẽ
     */
    private void handleAntiBotDelay(Page page) {
        int currentCount = requestCounter.incrementAndGet();
        try {
            if (currentCount % (25 + random.nextInt(11)) == 0) {
                long microBreak = 45000 + random.nextInt(30000);
                log.info("⏳ [Anti-Bot] Đã gửi {} requests. Nghỉ dài {} giây...", currentCount, microBreak / 1000);
                Thread.sleep(microBreak);

                if (page != null && page.context() != null) {
                    page.context().clearCookies();
                    log.info("🧹 [Anti-Bot] Đã tự động dọn dẹp Cookies.");
                }
                return;
            }
            long delay = 3000 + random.nextInt(4001);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
