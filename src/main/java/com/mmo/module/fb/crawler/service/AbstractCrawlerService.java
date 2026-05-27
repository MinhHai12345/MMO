package com.mmo.module.fb.crawler.service;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import lombok.extern.slf4j.Slf4j;

import java.util.Random;

@Slf4j
public abstract class AbstractCrawlerService {
    private final Random random = new Random();

    protected void randomDelay() {
        try {
            long delay = 1000 + random.nextInt(2000);
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
}
