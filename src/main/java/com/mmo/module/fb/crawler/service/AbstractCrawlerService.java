package com.mmo.module.fb.crawler.service;

import java.util.Random;

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
}
