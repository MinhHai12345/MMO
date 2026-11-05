package com.mmo.module.fb.ingest.adapter.out.persistence.core;

import com.mmo.module.fb.ingest.application.port.out.CrawlerHealthStatePort;
import com.mmo.module.fb.ingest.entity.CrawlerHealth;
import com.mmo.module.fb.ingest.repository.CrawlerHealthRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** JPA adapter for the legacy crawler-health table used as a bootstrap state store. */
@Repository
@RequiredArgsConstructor
public class CrawlerHealthStateJpaAdapter implements CrawlerHealthStatePort {
    private final CrawlerHealthRepository repository;

    @Override
    @Transactional(readOnly = true)
    public boolean isSuccessful(String crawlerName) {
        return repository.findByCrawlerName(crawlerName)
                .map(state -> "SUCCESS".equalsIgnoreCase(state.getStatus()))
                .orElse(false);
    }

    @Override
    @Transactional
    public void markRunning(String crawlerName, Instant startedAt) {
        CrawlerHealth state = findOrCreate(crawlerName);
        state.setStatus("RUNNING");
        state.setLastRun(startedAt);
        state.setLastErrorMessage(null);
        repository.save(state);
    }

    @Override
    @Transactional
    public void markSucceeded(String crawlerName, Instant completedAt) {
        CrawlerHealth state = findOrCreate(crawlerName);
        state.setStatus("SUCCESS");
        state.setLastSuccess(completedAt);
        state.setLastErrorMessage(null);
        repository.save(state);
    }

    @Override
    @Transactional
    public void markFailed(String crawlerName, Instant failedAt, String failureMessage) {
        CrawlerHealth state = findOrCreate(crawlerName);
        state.setStatus("FAILED");
        state.setLastRun(failedAt);
        state.setErrorCount((state.getErrorCount() == null ? 0 : state.getErrorCount()) + 1);
        state.setLastErrorMessage(failureMessage);
        repository.save(state);
    }

    private CrawlerHealth findOrCreate(String crawlerName) {
        return repository.findByCrawlerName(crawlerName)
                .orElseGet(() -> CrawlerHealth.builder()
                        .crawlerName(crawlerName)
                        .status("RUNNING")
                        .errorCount(0)
                        .build());
    }
}
