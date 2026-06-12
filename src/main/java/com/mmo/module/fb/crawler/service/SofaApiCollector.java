package com.mmo.module.fb.crawler.service;

import com.microsoft.playwright.Page;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class SofaApiCollector {
    private final Map<String, CompletableFuture<String>> futures = new ConcurrentHashMap<>();

    public void attach(Page page, String endpoint) {
        CompletableFuture<String> future = new CompletableFuture<>();
        futures.put(endpoint, future);
        page.onResponse(response -> {
            try {
                if (response.ok() && response.url().contains(endpoint)) {
                    future.complete(response.text());
                }
            } catch (Exception ex) {
                future.completeExceptionally(ex);
            }
        });
    }

    public String waitResponse(String endpoint) {
        try {
            return futures.get(endpoint).get(30, TimeUnit.SECONDS);
        } catch (Exception ex) {
            return null;
        }
    }
}
