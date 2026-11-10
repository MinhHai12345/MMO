package com.mmo.module.fb.workflow.application.service;

import com.mmo.module.fb.analytics.service.AdvancedMetricsProcessingService;
import com.mmo.module.fb.analytics.service.MatchPredictionProcessingService;
import com.mmo.module.fb.content.entity.ContentWorkflowRunEntity;
import com.mmo.module.fb.content.service.ContentDraftService.DraftFormat;
import com.mmo.module.fb.content.service.ContentLifecycleService;
import com.mmo.module.fb.content.service.ContentWorkflowOrchestrator.WorkflowCommand;
import com.mmo.module.fb.ingest.application.port.in.FullDataRebuildUseCase;
import com.mmo.module.fb.workflow.application.port.in.FootballWorkflowUseCase;
import com.mmo.module.fb.workflow.application.port.in.FootballWorkflowUseCase.WorkflowReport;
import com.mmo.module.fb.workflow.application.port.in.FootballWorkflowUseCase.WorkflowStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Synchronous application coordinator for the football data-to-content pipeline.
 *
 * <p>It invokes application use cases directly; it never schedules or waits for nested
 * Quartz jobs. The existing ingestion rebuild use case owns the core-sync and enrichment
 * ordering, while this coordinator orders analytics and content after that prerequisite.</p>
 *
 * <p>Durable workflow-run/stage checkpoints are intentionally handled by a subsequent
 * persistence layer. Content generation already receives the same UTC-date idempotency
 * key as the existing scheduled content job, preventing duplicate daily drafts.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FootballWorkflowCoordinator implements FootballWorkflowUseCase {

    private static final int CONTENT_LIMIT = 20;
    private static final int CONTENT_WINDOW_DAYS = 15;

    private final FullDataRebuildUseCase fullDataRebuildUseCase;
    private final AdvancedMetricsProcessingService advancedMetricsProcessingService;
    private final MatchPredictionProcessingService matchPredictionProcessingService;
    private final ContentLifecycleService contentLifecycleService;

    @Override
    public WorkflowReport runDaily() {
        return run(LocalDate.now(ZoneOffset.UTC), () -> false);
    }

    @Override
    public WorkflowReport run(LocalDate utcDate, BooleanSupplier stopRequested) {
        if (utcDate == null) {
            throw new IllegalArgumentException("utcDate is required");
        }
        BooleanSupplier stop = stopRequested == null ? () -> false : stopRequested;
        Instant startedAt = Instant.now();
        List<WorkflowStep> steps = new ArrayList<>();
        Long contentRunId = null;
        int draftsGenerated = 0;
        int contentFailures = 0;

        if (stop.getAsBoolean()) {
            return report("INTERRUPTED", utcDate, startedAt, steps, null, 0, 0);
        }

        StageOutcome ingestion = executeStage("DATA_INGESTION_AND_ENRICHMENT",
                fullDataRebuildUseCase::rebuildAllData);
        steps.add(ingestion.step());
        if (!ingestion.successful()) {
            return report("FAILED", utcDate, startedAt, steps, null, 0, 0);
        }
        if (stop.getAsBoolean()) {
            return report("INTERRUPTED", utcDate, startedAt, steps, null, 0, 0);
        }

        long metricsStarted = System.nanoTime();
        try {
            var metrics = advancedMetricsProcessingService.process(stop);
            String status = metrics.status();
            steps.add(step("ADVANCED_METRICS", status, metricsStarted,
                    "teamFormsUpdated=" + metrics.teamFormsUpdated()
                            + ", h2hRecordsUpdated=" + metrics.h2hRecordsUpdated()));
            if ("INTERRUPTED".equals(status) || stop.getAsBoolean()) {
                return report("INTERRUPTED", utcDate, startedAt, steps, null, 0, 0);
            }
            if (!"COMPLETED".equals(status)) {
                return report("FAILED", utcDate, startedAt, steps, null, 0, 0);
            }
        } catch (RuntimeException exception) {
            steps.add(step("ADVANCED_METRICS", "FAILED", metricsStarted, describe(exception)));
            log.error("Football workflow failed at ADVANCED_METRICS", exception);
            return report("FAILED", utcDate, startedAt, steps, null, 0, 0);
        }

        if (stop.getAsBoolean()) {
            return report("INTERRUPTED", utcDate, startedAt, steps, null, 0, 0);
        }

        long predictionStarted = System.nanoTime();
        try {
            var predictions = matchPredictionProcessingService.processUpcomingMatches(stop);
            String status = predictions.status();
            steps.add(step("PREDICTIONS", status, predictionStarted,
                    "candidates=" + predictions.candidates()
                            + ", processed=" + predictions.processed()
                            + ", skipped=" + predictions.skipped()
                            + ", failed=" + predictions.failed()));
            if (predictions.stopped() || stop.getAsBoolean()) {
                return report("INTERRUPTED", utcDate, startedAt, steps, null, 0, 0);
            }
            if (predictions.failed() > 0) {
                return report("FAILED", utcDate, startedAt, steps, null, 0, 0);
            }
        } catch (RuntimeException exception) {
            steps.add(step("PREDICTIONS", "FAILED", predictionStarted, describe(exception)));
            log.error("Football workflow failed at PREDICTIONS", exception);
            return report("FAILED", utcDate, startedAt, steps, null, 0, 0);
        }

        if (stop.getAsBoolean()) {
            return report("INTERRUPTED", utcDate, startedAt, steps, null, 0, 0);
        }

        long contentStarted = System.nanoTime();
        try {
            Instant from = utcDate.atStartOfDay().toInstant(ZoneOffset.UTC);
            Instant to = utcDate.plusDays(CONTENT_WINDOW_DAYS).atStartOfDay().toInstant(ZoneOffset.UTC);
            WorkflowCommand command = new WorkflowCommand(from, to, null, CONTENT_LIMIT, DraftFormat.PRE_MATCH);
            String idempotencyKey = "scheduled-content-" + utcDate;
            ContentWorkflowRunEntity contentRun = contentLifecycleService.generate(command, idempotencyKey);
            contentRunId = contentRun.getId();
            draftsGenerated = contentRun.getDraftsGenerated();
            contentFailures = contentRun.getFailures();
            String status = contentRun.getStatus() == null ? "UNKNOWN" : contentRun.getStatus();
            steps.add(step("PRE_MATCH_CONTENT", status, contentStarted,
                    "runId=" + contentRunId + ", draftsGenerated=" + draftsGenerated
                            + ", failures=" + contentFailures));
            String overallStatus = contentFailures > 0 ? "COMPLETED_WITH_ERRORS" : "COMPLETED";
            return report(overallStatus, utcDate, startedAt, steps,
                    contentRunId, draftsGenerated, contentFailures);
        } catch (RuntimeException exception) {
            steps.add(step("PRE_MATCH_CONTENT", "FAILED", contentStarted, describe(exception)));
            log.error("Football workflow failed at PRE_MATCH_CONTENT", exception);
            return report("FAILED", utcDate, startedAt, steps, null, 0, 1);
        }
    }

    private StageOutcome executeStage(String name, Runnable operation) {
        long started = System.nanoTime();
        try {
            log.info("Football workflow stage STARTED: {}", name);
            operation.run();
            WorkflowStep step = step(name, "COMPLETED", started, "Stage completed.");
            log.info("Football workflow stage COMPLETED: {} durationMs={}", name, step.durationMillis());
            return new StageOutcome(step, true);
        } catch (RuntimeException exception) {
            WorkflowStep step = step(name, "FAILED", started, describe(exception));
            log.error("Football workflow stage FAILED: {}", name, exception);
            return new StageOutcome(step, false);
        }
    }

    private WorkflowStep step(String name, String status, long startedNanos, String message) {
        return new WorkflowStep(name, status,
                Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000), message);
    }

    private WorkflowReport report(String status, LocalDate utcDate, Instant startedAt,
                                   List<WorkflowStep> steps, Long contentRunId,
                                   int draftsGenerated, int contentFailures) {
        return new WorkflowReport(status, utcDate, startedAt, Instant.now(), steps,
                contentRunId, draftsGenerated, contentFailures);
    }

    private String describe(RuntimeException exception) {
        String message = exception.getMessage();
        return exception.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private record StageOutcome(WorkflowStep step, boolean successful) {
    }
}
