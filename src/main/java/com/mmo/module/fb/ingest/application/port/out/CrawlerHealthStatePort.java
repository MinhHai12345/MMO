package com.mmo.module.fb.ingest.application.port.out;

import java.time.Instant;

/** Outbound port for durable crawler/bootstrap execution state. */
public interface CrawlerHealthStatePort {
    boolean isSuccessful(String crawlerName);
    void markRunning(String crawlerName, Instant startedAt);
    void markSucceeded(String crawlerName, Instant completedAt);
    void markFailed(String crawlerName, Instant failedAt, String failureMessage);
}
