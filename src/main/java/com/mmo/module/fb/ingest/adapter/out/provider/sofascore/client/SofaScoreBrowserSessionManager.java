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
    private final AtomicInteger activePages = new AtomicInteger();

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
            if (pageUses.get() >= MAX_PAGE_USES && activePages.get() == 0) {
                closeSession();
                ensureSession();
            }

            Page page = context.newPage();
            pageUses.incrementAndGet();
            activePages.incrementAndGet();
            // Log every request initiated by this page, including document, fetch/XHR,
            // scripts, stylesheets, images, fonts and third-party resources.
            page.onRequest(request -> log.info(
                    "[SOFA_SCORE][NETWORK][REQUEST] method={}, resourceType={}, url={}",
                    request.method(), request.resourceType(), request.url()));

            // Log every response, not only failed responses. This makes it possible to
            // correlate each requested endpoint with its HTTP status.
            page.onResponse(response -> {
                var request = response.request();
                log.info("[SOFA_SCORE][NETWORK][RESPONSE] status={}, method={}, resourceType={}, url={}",
                        response.status(), request.method(), request.resourceType(), response.url());
            });

            page.onRequestFailed(request -> {
                String failure = request.failure() == null ? "unknown" : request.failure();
                log.warn("[SOFA_SCORE][NETWORK][FAILED] method={}, resourceType={}, url={}, failure={}",
                        request.method(), request.resourceType(), request.url(), failure);
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
        } catch (PlaywrightException exception) {
            log.warn("[SOFA_SCORE] Could not close browser page cleanly: {}", exception.getMessage());
        } finally {
            if (page != null) {
                activePages.updateAndGet(active -> Math.max(0, active - 1));
            }
            if (pageUses.get() >= MAX_PAGE_USES && context != null && activePages.get() == 0) {
                closeSession();
            }
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
                ? Paths.get(System.getProperty("user.dir"), "sofascore_bot_profile1")
                : Paths.get(configuredProfile).toAbsolutePath().normalize();

        try {
            Files.createDirectories(profilePath);
            playwright = Playwright.create();
            String configuredChannel = config.getOption("browser-channel");
            String browserChannel = configuredChannel == null || configuredChannel.isBlank()
                    ? "chrome" : configuredChannel.trim();
            String configuredHeadless = config.getOption("headless");
            boolean headless = configuredHeadless != null && Boolean.parseBoolean(configuredHeadless);

            BrowserType.LaunchPersistentContextOptions options =
                    new BrowserType.LaunchPersistentContextOptions()
                            .setHeadless(headless)
                            .setIgnoreDefaultArgs(List.of("--enable-automation"))
                            .setArgs(List.of(
                                    "--disable-blink-features=AutomationControlled",
                                    "--no-first-run",
                                    "--no-default-browser-check",
                                    "--start-maximized"
                            ))
                            .setViewportSize(null);
            if (!"chromium".equalsIgnoreCase(browserChannel)) {
                options.setChannel(browserChannel);
            }

            context = playwright.chromium().launchPersistentContext(profilePath, options);
            context.addInitScript(
                    "Object.defineProperty(navigator, 'webdriver', { get: () => undefined });");
            log.info("[SOFA_SCORE] Persistent browser session started: channel={}, headless={}, profile={}",
                    browserChannel, headless, profilePath);
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
        activePages.set(0);
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
