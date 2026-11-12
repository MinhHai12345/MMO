package com.mmo.module.fb.ingest.adapter.out.provider.sofascore.client;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.enums.Provider;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Owns the single persistent SofaScore browser session.
 *
 * Playwright Java objects are not thread-safe. The lease lock is deliberately held from
 * openPage() until closePage() so all browser operations run serially on one calling thread.
 */
@Slf4j
@Component
public class SofaScoreBrowserSessionManager {
    private static final int MAX_PAGE_USES = 150;

    private final AppProperties properties;
    private final ReentrantLock leaseLock = new ReentrantLock(true);
    private final AtomicInteger pageUses = new AtomicInteger();

    private Playwright playwright;
    private BrowserContext context;

    public SofaScoreBrowserSessionManager(AppProperties properties) {
        this.properties = properties;
    }

    public Page openPage() {
        leaseLock.lock();
        boolean leaseTransferred = false;
        try {
            ensureSession();
            if (pageUses.get() >= MAX_PAGE_USES && context.pages().isEmpty()) {
                closeSession();
                ensureSession();
            }

            Page page = context.newPage();
            pageUses.incrementAndGet();
            page.onRequestFailed(request -> {
                String failure = request.failure() == null ? "unknown" : request.failure();
                log.warn("[SOFA_SCORE] Browser request failed: method={}, url={}, failure={}",
                        request.method(), request.url(), failure);
            });
            page.onResponse(response -> {
                if (response.status() >= 400) {
                    log.warn("[SOFA_SCORE] Browser response error: status={}, url={}",
                            response.status(), response.url());
                }
            });
            leaseTransferred = true;
            return page;
        } catch (RuntimeException exception) {
            closeSession();
            throw exception;
        } finally {
            if (!leaseTransferred) {
                leaseLock.unlock();
            }
        }
    }

    public void closePage(Page page) {
        try {
            if (page != null && !page.isClosed()) {
                page.close();
            }
            if (pageUses.get() >= MAX_PAGE_USES && context != null && context.pages().isEmpty()) {
                closeSession();
            }
        } catch (PlaywrightException exception) {
            log.warn("[SOFA_SCORE] Could not close browser page cleanly: {}", exception.getMessage());
        } finally {
            if (leaseLock.isHeldByCurrentThread()) {
                leaseLock.unlock();
            }
        }
    }

    private void ensureSession() {
        if (context != null && playwright != null) {
            return;
        }

        AppProperties.ProviderConfig config = sofaScoreConfig();
        String configuredProfile = config.getOption("profile-dir");
        Path profilePath = configuredProfile == null || configuredProfile.isBlank()
                ? Paths.get(System.getProperty("user.home"), ".mmo", "sofascore", "chrome-profile")
                : Paths.get(configuredProfile).toAbsolutePath().normalize();

        try {
            Files.createDirectories(profilePath);
            boolean manualIntervention = Boolean.parseBoolean(
                    config.getOption("manual-captcha-intervention"));
            String headlessValue = config.getOption("headless");
            boolean headless = !manualIntervention
                    && (headlessValue == null || headlessValue.isBlank() || Boolean.parseBoolean(headlessValue));
            String channel = config.getOption("browser-channel");
            if (channel == null || channel.isBlank()) {
                channel = "chrome";
            }

            playwright = Playwright.create();
            BrowserType.LaunchPersistentContextOptions options =
                    new BrowserType.LaunchPersistentContextOptions()
                            .setChannel(channel)
                            .setHeadless(headless)
                            .setArgs(List.of(
                                    "--no-sandbox",
                                    "--disable-dev-shm-usage",
                                    "--disable-gpu",
                                    "--no-first-run",
                                    "--no-default-browser-check"
                            ))
                            .setViewportSize(1920, 1080)
                            .setLocale("en-US")
                            .setTimezoneId("Asia/Ho_Chi_Minh");

            context = playwright.chromium().launchPersistentContext(profilePath, options);
            log.info("[SOFA_SCORE] Persistent Chrome session started: channel={}, headless={}, profile={}",
                    channel, headless, profilePath);
        } catch (Exception exception) {
            closeSession();
            throw new SofaScoreFetchException("Could not initialize the persistent SofaScore browser session",
                    exception);
        }
    }

    private AppProperties.ProviderConfig sofaScoreConfig() {
        AppProperties.ProviderConfig config =
                properties.getIngestion().getProviderConfig().get(Provider.SOFA_SCORE);
        if (config == null || !config.isEnabled()) {
            throw new IllegalStateException("SofaScore provider is disabled or not configured");
        }
        return config;
    }

    private void closeSession() {
        if (context != null) {
            try {
                context.close();
            } catch (Exception exception) {
                log.debug("[SOFA_SCORE] Error closing persistent browser context: {}", exception.getMessage());
            } finally {
                context = null;
            }
        }
        if (playwright != null) {
            try {
                playwright.close();
            } catch (Exception exception) {
                log.debug("[SOFA_SCORE] Error closing Playwright: {}", exception.getMessage());
            } finally {
                playwright = null;
            }
        }
        pageUses.set(0);
    }

    @PreDestroy
    public void shutdown() {
        leaseLock.lock();
        try {
            closeSession();
        } finally {
            leaseLock.unlock();
        }
    }
}
