package com.mmo.module.fb.ingest.provider.sofascore.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.WaitUntilState;
import com.mmo.module.fb.ingest.provider.model.DynamicFetchResult;
import lombok.extern.slf4j.Slf4j;
import org.thymeleaf.util.MapUtils;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

@Slf4j
public abstract class AbstractSofaScoreClient {
    protected final ObjectMapper objectMapper = new ObjectMapper();

    protected DynamicFetchResult safeFetch(
            String loadPageUrl,
            Map<String, Class<?>> endpointClassMap,
            Page page,
            Consumer<Page> triggerAction) {
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
            } catch (Exception exception) {
                log.debug("Could not read intercepted SofaScore response: {}", exception.getMessage());
            }
        };

        page.onResponse(responseHandler);
        try {
            page.navigate(loadPageUrl, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.COMMIT)
                    .setTimeout(30000));
            page.waitForTimeout(7000);
            if (triggerAction != null) {
                page.waitForTimeout(8000);
                try {
                    triggerAction.accept(page);
                    page.waitForTimeout(10000);
                } catch (Exception exception) {
                    log.warn("SofaScore trigger action failed: {}", exception.getMessage());
                }
            }
            CompletableFuture<?>[] futuresArray = futuresMap.values().toArray(new CompletableFuture[0]);
            CompletableFuture.allOf(futuresArray).get(20, TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            log.warn("Timed out waiting for all intercepted SofaScore endpoints; parsing available responses");
        } catch (Exception exception) {
            log.warn("Failed while loading SofaScore page {}: {}", loadPageUrl, exception.getMessage());
        } finally {
            page.offResponse(responseHandler);
        }

        futuresMap.forEach((endpoint, future) -> {
            if (future.isDone() && !future.isCompletedExceptionally()) {
                try {
                    String json = future.getNow(null);
                    if (json != null && !json.isEmpty()) {
                        fetchResult.put(endpoint, objectMapper.readValue(json, endpointClassMap.get(endpoint)));
                    }
                } catch (Exception exception) {
                    throw new SofaScoreFetchException("Could not parse SofaScore endpoint " + endpoint, exception);
                }
            } else {
                log.warn("SofaScore endpoint was not captured: {}", endpoint);
            }
        });
        return fetchResult;
    }
}
