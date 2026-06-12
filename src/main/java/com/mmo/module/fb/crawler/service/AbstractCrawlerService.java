package com.mmo.module.fb.crawler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.WaitUntilState;
import com.mmo.module.fb.crawler.model.DynamicFetchResult;
import lombok.extern.slf4j.Slf4j;
import org.thymeleaf.util.MapUtils;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Slf4j
public abstract class AbstractCrawlerService {
    private final Random random = new Random();
    protected final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicInteger requestCounter = new AtomicInteger(0);


    /**
     * Hàm Generic xử lý cào dữ liệu an toàn, tự động chống block.
     *
     * @param <T>   Kiểu dữ liệu trả về mong muốn
     * @param page  Đối tượng Playwright Page
     * @param clazz Class Mock Data để Jackson parse JSON (T.class)
     * @return Đối tượng đã được parse, hoặc null nếu lỗi
     */
    protected <T> T safeFetch(String url, Page page, Class<T> clazz) {
        try {
            handleAntiBotDelay(page);
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

    /**
     * Hàm safeFetch cải tiến: Tự động lắng nghe nhiều API và parse trực tiếp sang Class tương ứng.
     *
     * @param loadPageUrl      URL trang cần navigate tới
     * @param endpointClassMap Map cấu hình dạng: <Đoạn chuỗi Endpoint, Class mong muốn parse>
     * @param page             Instance Page của Playwright
     * @return Đối tượng DynamicFetchResult chứa các Object đã được tự động parse
     */
    protected DynamicFetchResult safeFetch(String loadPageUrl, Map<String, Class<?>> endpointClassMap, Page page, Consumer<Page> triggerAction) {
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
            } catch (Exception ignored) {
                // Tránh làm gián đoạn luồng nội bộ của Playwright
            }
        };
        page.onResponse(responseHandler);
        try {
            page.navigate(loadPageUrl, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.COMMIT)
                    .setTimeout(30000));
            if (triggerAction != null) {
                page.waitForTimeout(5000);
                try {
                    log.info("⚡ [Pipeline] Kích nổ hành động Trigger...");
                    triggerAction.accept(page);
                } catch (Exception ex) {
                    log.error("❌ Lỗi xảy ra khi thực thi Trigger Action: {}", ex.getMessage());
                }
            }
            page.waitForTimeout(15000);

            // 5. Đợi toàn bộ các API trả về kết quả hoặc chạm ngưỡng giới hạn thời gian (Timeout)
            CompletableFuture<?>[] futuresArray = futuresMap.values().toArray(new CompletableFuture[0]);
            CompletableFuture.allOf(futuresArray).get(20, TimeUnit.SECONDS);

        } catch (TimeoutException e) {
            log.warn("⏳ Hết thời gian chờ. Một số API phản hồi chậm, hệ thống sẽ tiến hành parse những dữ liệu đã kịp về.");
        } catch (Exception e) {
            log.error("❌ Lỗi nghiêm trọng khi thực hiện safeFetch đa API cho URL [{}]: {}", loadPageUrl, e.getMessage(), e);
        } finally {
            page.offResponse(responseHandler);
        }

        futuresMap.forEach((endpoint, future) -> {
            if (future.isDone() && !future.isCompletedExceptionally()) {
                try {
                    String json = future.getNow(null);
                    if (json != null && !json.isEmpty()) {
                        Class<?> targetClass = endpointClassMap.get(endpoint);
                        Object parsedObject = objectMapper.readValue(json, targetClass);
                        fetchResult.put(endpoint, parsedObject);
                    }
                } catch (Exception ex) {
                    log.error("❌ Lỗi xảy ra khi tự động ép kiểu Json cho endpoint [{}]: {}", endpoint, ex.getMessage());
                }
            } else {
                log.warn("⚠️ Bỏ sót hoặc không bắt được dữ liệu từ API: {}", endpoint);
            }
        });
        return fetchResult;
    }
}
