package com.mmo.module.fb.ingest.adapter.out.provider.sofascore;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitUntilState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class CloudflareBypassTest {
    public static void main(String[] args) {
        // 1. Dùng một thư mục profile hoàn toàn độc lập với Chrome cá nhân đang mở
        // Đường dẫn tuyệt đối giúp tránh Chrome hiểu nhầm là mở vào session cũ
        String configuredProfile = System.getenv("SOFA_SCORE_PROFILE_DIR");
        Path userDataDir = configuredProfile == null || configuredProfile.isBlank()
                ? Paths.get(System.getProperty("user.home"), ".mmo", "sofascore", "chrome-profile")
                : Paths.get(configuredProfile).toAbsolutePath().normalize();
        try {
            Files.createDirectories(userDataDir);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create the SofaScore browser profile directory", exception);
        }
        System.out.println("Đang khởi chạy Chrome thật với profile riêng tại: " + userDataDir);

        try (Playwright playwright = Playwright.create()) {

            // 2. Cấu hình dùng Chrome thật (.setChannel("chrome")) và ẩn cờ automation
            BrowserType.LaunchPersistentContextOptions options = new BrowserType.LaunchPersistentContextOptions()
                    .setChannel("chrome") // Dùng trình duyệt Google Chrome thật trên máy để vượt qua kiểm tra TLS/DRM của Cloudflare
                    .setHeadless(false) // Bật giao diện ở lần đầu để bạn tự click xác thực nếu hiện Captcha
                    .setIgnoreDefaultArgs(Arrays.asList("--enable-automation")) // Xóa thanh thông báo "Chrome is being controlled..."
                    .setArgs(Arrays.asList(
                            "--disable-blink-features=AutomationControlled",
                            "--no-first-run",
                            "--no-default-browser-check",
                            "--start-maximized"
                    ))
                    .setViewportSize(null); // Giữ kích thước cửa sổ tự nhiên theo màn hình thật

            // Khởi tạo Persistent Context (lưu lại cookie vĩnh viễn sau lần xác thực đầu tiên)
            BrowserContext context = playwright.chromium().launchPersistentContext(userDataDir, options);

            // Chèn script xóa dấu vết navigator.webdriver
            context.addInitScript("Object.defineProperty(navigator, 'webdriver', { get: () => undefined });");

            Page page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);

            // 3. Lắng nghe và bắt gói tin API JSON từ SofaScore
            page.onResponse(response -> {
                String url = response.url();
                if (url.contains("/api/v1/config/default-unique-tournaments/VN/football")) {
                    System.out.println("\n[+] BẮT THÀNH CÔNG API: " + url);
                    try {
                        String jsonContent = response.text();
                        Path outputDir = Paths.get("output_data");
                        if (!Files.exists(outputDir)) {
                            Files.createDirectories(outputDir);
                        }
                        String fileName = "output_data/data_" + System.currentTimeMillis() + ".json";
                        Files.writeString(Paths.get(fileName), jsonContent);
                        System.out.println("-> Đã lưu dữ liệu JSON vào: " + fileName);
                    } catch (Exception e) {
                        System.err.println("Lỗi đọc/lưu JSON: " + e.getMessage());
                    }
                }
            });

            // 4. Điều hướng với cơ chế tự động thử lại (chống lỗi rớt mạng ERR_CONNECTION_RESET tại VN)
            String targetUrl = "https://www.sofascore.com";
            boolean loaded = navigateWithRetry(page, targetUrl, 6);

            if (loaded) {
                System.out.println("\n=======================================================");
                System.out.println("Trang đã tải xong!");
                System.out.println("Nếu thấy ô 'Verify you are human', hãy tự tay bấm vào đó 1 lần.");
                System.out.println("Hệ thống sẽ đợi 20 giây để lưu Cookie và bắt các gói tin API...");
                System.out.println("=======================================================\n");

                // Cuộn trang nhẹ nhàng mô phỏng người dùng thật
                page.waitForTimeout(3000);
                page.mouse().wheel(0, 500);

                // Giữ trình duyệt mở 20 giây để bạn thao tác nếu cần và gom API
                page.waitForTimeout(20000);
            } else {
                System.err.println("Không thể kết nối tới SofaScore sau nhiều lần thử. Hãy kiểm tra mạng hoặc bật 1.1.1.1 WARP.");
            }

            // Đóng context (toàn bộ Cookie đã vượt Cloudflare sẽ được lưu vào thư mục sofascore_bot_profile)
            context.close();
            System.out.println("Đã đóng trình duyệt và lưu phiên làm việc thành công!");

        } catch (Exception e) {
            System.err.println("Lỗi hệ thống: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Hàm tự động thử lại khi gặp lỗi rớt kết nối mạng (net::ERR_CONNECTION_RESET)
     */
    private static boolean navigateWithRetry(Page page, String url, int maxRetries) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                System.out.println("Lần thử " + attempt + "/" + maxRetries + " - Đang truy cập: " + url);
                page.navigate(url, new Page.NavigateOptions()
                        .setTimeout(30000)
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                return true;
            } catch (PlaywrightException e) {
                System.out.println("-> Rớt kết nối ở lần " + attempt + " (" + e.getMessage().split("\n")[0] + ")");
                if (attempt < maxRetries) {
                    System.out.println("-> Đợi 2 giây trước khi tự động tải lại...");
                    page.waitForTimeout(2000);
                }
            }
        }
        return false;
    }
}
