package com.mmo.module.fb.workflow.application.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Keeps a claimed workflow lease alive while a synchronous workflow stage is running.
 * Each heartbeat uses the checkpoint service's independent transaction.
 */
@Slf4j
@Component
public class WorkflowLeaseHeartbeat {

    private final WorkflowCheckpointService checkpointService;
    private final Duration heartbeatInterval;
    private final ScheduledExecutorService scheduler;

    public WorkflowLeaseHeartbeat(
            WorkflowCheckpointService checkpointService,
            @Value("${mmo.workflow.lease.heartbeat-interval:PT1M}") Duration heartbeatInterval) {
        this.checkpointService = checkpointService;
        this.heartbeatInterval = heartbeatInterval;
        ThreadFactory threadFactory = task -> {
            Thread thread = new Thread(task, "workflow-lease-heartbeat");
            thread.setDaemon(true);
            return thread;
        };
        this.scheduler = Executors.newScheduledThreadPool(2, threadFactory);
    }

    public Heartbeat start(Long runId, String executionToken) {
        if (runId == null || executionToken == null || executionToken.isBlank()) {
            throw new IllegalArgumentException("Workflow run and execution token are required for heartbeat.");
        }
        if (heartbeatInterval == null || heartbeatInterval.isNegative() || heartbeatInterval.toMillis() < 1) {
            throw new IllegalStateException("Workflow lease heartbeat interval must be positive.");
        }

        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
            if (failure.get() != null) return;
            try {
                checkpointService.renewLease(runId, executionToken);
            } catch (RuntimeException exception) {
                failure.compareAndSet(null, exception);
                log.error("Workflow lease heartbeat failed for runId={}; current worker must stop checkpoint writes.",
                        runId, exception);
            }
        }, heartbeatInterval.toMillis(), heartbeatInterval.toMillis(), TimeUnit.MILLISECONDS);

        return new Heartbeat(task, failure);
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }

    public static final class Heartbeat implements AutoCloseable {

        private final ScheduledFuture<?> task;
        private final AtomicReference<RuntimeException> failure;

        private Heartbeat(ScheduledFuture<?> task, AtomicReference<RuntimeException> failure) {
            this.task = task;
            this.failure = failure;
        }

        public void assertHealthy() {
            RuntimeException heartbeatFailure = failure.get();
            if (heartbeatFailure != null) {
                throw new IllegalStateException(
                        "Workflow execution lease heartbeat was lost; refusing to persist stage completion.",
                        heartbeatFailure);
            }
        }

        @Override
        public void close() {
            task.cancel(false);
        }
    }
}
