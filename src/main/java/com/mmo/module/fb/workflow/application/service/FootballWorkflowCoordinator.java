package com.mmo.module.fb.workflow.application.service;

import com.mmo.module.fb.analytics.service.AdvancedMetricsProcessingService;
import com.mmo.module.fb.analytics.service.MatchPredictionProcessingService;
import com.mmo.module.fb.content.entity.ContentWorkflowRunEntity;
import com.mmo.module.fb.content.repository.ContentWorkflowRunRepository;
import com.mmo.module.fb.content.service.ContentDraftService.DraftFormat;
import com.mmo.module.fb.content.service.ContentLifecycleService;
import com.mmo.module.fb.content.service.ContentWorkflowOrchestrator.WorkflowCommand;
import com.mmo.module.fb.ingest.application.port.in.FullDataRebuildUseCase;
import com.mmo.module.fb.workflow.application.port.in.FootballWorkflowUseCase;
import com.mmo.module.fb.workflow.application.port.in.FootballWorkflowUseCase.WorkflowReport;
import com.mmo.module.fb.workflow.application.port.in.FootballWorkflowUseCase.WorkflowStep;
import com.mmo.module.fb.workflow.domain.enums.FootballWorkflowStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

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
    private final ContentWorkflowRunRepository contentWorkflowRunRepository;
    private final WorkflowCheckpointService checkpointService;
    private final DataReadinessGateService readinessGateService;

    @Override
    public WorkflowReport runDaily() {
        return run(LocalDate.now(ZoneOffset.UTC), () -> false);
    }

    @Override
    public WorkflowReport run(LocalDate utcDate, BooleanSupplier stopRequested) {
        if (utcDate == null) throw new IllegalArgumentException("utcDate is required");
        BooleanSupplier stop = stopRequested == null ? () -> false : stopRequested;
        Instant startedAt = Instant.now();
        List<WorkflowStep> steps = new ArrayList<>();
        Long contentRunId = null;
        int draftsGenerated = 0;
        int contentFailures = 0;
        Long workflowRunId = getOrCreateRun("football-workflow-" + utcDate);

        if (checkpointService.isRunCompleted(workflowRunId)) {
            ContentWorkflowRunEntity completedContentRun = contentWorkflowRunRepository
                    .findByIdempotencyKey("scheduled-content-" + utcDate).orElse(null);
            return report("COMPLETED", utcDate, startedAt, steps,
                    completedContentRun == null ? null : completedContentRun.getId(),
                    completedContentRun == null ? 0 : completedContentRun.getDraftsGenerated(),
                    completedContentRun == null ? 0 : completedContentRun.getFailures());
        }
        String executionToken = checkpointService.claimRun(workflowRunId);
        if (executionToken == null) {
            return report("ALREADY_RUNNING", utcDate, startedAt, steps, null, 0, 0);
        }

        if (!executeStage(workflowRunId, executionToken, FootballWorkflowStage.DATA_INGESTION, stop,
                () -> {
                    fullDataRebuildUseCase.rebuildAllData();
                    return "Core data ingestion and enrichment completed.";
                }, steps)) {
            if (stop.getAsBoolean()) checkpointService.releaseRun(workflowRunId, executionToken);
            return report(stageStatus(stop, steps), utcDate, startedAt, steps, null, 0, 0);
        }

        if (!executeStage(workflowRunId, executionToken, FootballWorkflowStage.DATA_READINESS, stop,
                () -> {
                    DataReadinessGateService.ReadinessReport readiness = readinessGateService.evaluate();
                    if (!readiness.ready()) {
                        String details = readiness.seasons().stream()
                                .filter(season -> !season.issues().isEmpty())
                                .map(season -> season.league() + " " + season.season() + ": "
                                        + String.join(",", season.issues()))
                                .reduce((left, right) -> left + "; " + right).orElse("No season readiness data.");
                        throw new IllegalStateException("Data readiness gate rejected analytics processing: " + details);
                    }
                    return "Validated " + readiness.seasons().size() + " league-season datasets.";
                }, steps)) {
            if (stop.getAsBoolean()) checkpointService.releaseRun(workflowRunId, executionToken);
            return report(stageStatus(stop, steps), utcDate, startedAt, steps, null, 0, 0);
        }

        if (!executeStage(workflowRunId, executionToken, FootballWorkflowStage.ADVANCED_METRICS, stop,
                () -> {
                    var metrics = advancedMetricsProcessingService.process(stop);
                    if (!"COMPLETED".equals(metrics.status())) {
                        throw new IllegalStateException("Advanced metrics processing status: " + metrics.status());
                    }
                    return "teamFormsUpdated=" + metrics.teamFormsUpdated()
                            + ", h2hRecordsUpdated=" + metrics.h2hRecordsUpdated();
                }, steps)) {
            if (stop.getAsBoolean()) checkpointService.releaseRun(workflowRunId, executionToken);
            return report(stageStatus(stop, steps), utcDate, startedAt, steps, null, 0, 0);
        }

        if (!executeStage(workflowRunId, executionToken, FootballWorkflowStage.PREDICTIONS, stop,
                () -> {
                    var predictions = matchPredictionProcessingService.processUpcomingMatches(stop);
                    if (predictions.stopped() || predictions.failed() > 0) {
                        throw new IllegalStateException("Prediction processing incomplete: candidates="
                                + predictions.candidates() + ", processed=" + predictions.processed()
                                + ", failed=" + predictions.failed());
                    }
                    return "candidates=" + predictions.candidates() + ", processed="
                            + predictions.processed() + ", skipped=" + predictions.skipped();
                }, steps)) {
            if (stop.getAsBoolean()) checkpointService.releaseRun(workflowRunId, executionToken);
            return report(stageStatus(stop, steps), utcDate, startedAt, steps, null, 0, 0);
        }

        final ContentWorkflowRunEntity[] contentRunHolder = new ContentWorkflowRunEntity[1];
        if (!executeStage(workflowRunId, executionToken, FootballWorkflowStage.PRE_MATCH_CONTENT, stop,
                () -> {
                    Instant from = utcDate.atStartOfDay().toInstant(ZoneOffset.UTC);
                    Instant to = utcDate.plusDays(CONTENT_WINDOW_DAYS).atStartOfDay().toInstant(ZoneOffset.UTC);
                    WorkflowCommand command = new WorkflowCommand(from, to, null, CONTENT_LIMIT, DraftFormat.PRE_MATCH);
                    contentRunHolder[0] = contentLifecycleService.generate(command, "scheduled-content-" + utcDate);
                    ContentWorkflowRunEntity contentRun = contentRunHolder[0];
                    return "runId=" + contentRun.getId() + ", draftsGenerated=" + contentRun.getDraftsGenerated()
                            + ", failures=" + contentRun.getFailures();
                }, steps)) {
            if (stop.getAsBoolean()) checkpointService.releaseRun(workflowRunId, executionToken);
            return report(stageStatus(stop, steps), utcDate, startedAt, steps, null, 0, 0);
        }

        ContentWorkflowRunEntity completedContentRun = contentRunHolder[0] != null
                ? contentRunHolder[0]
                : contentWorkflowRunRepository.findByIdempotencyKey("scheduled-content-" + utcDate).orElse(null);
        if (completedContentRun != null) {
            contentRunId = completedContentRun.getId();
            draftsGenerated = completedContentRun.getDraftsGenerated();
            contentFailures = completedContentRun.getFailures();
        }
        checkpointService.completeRun(workflowRunId, executionToken);
        return report(contentFailures > 0 ? "COMPLETED_WITH_ERRORS" : "COMPLETED",
                utcDate, startedAt, steps, contentRunId, draftsGenerated, contentFailures);
    }

    private Long getOrCreateRun(String idempotencyKey) {
        try {
            return checkpointService.getOrCreateRun(idempotencyKey);
        } catch (DataIntegrityViolationException concurrentCreation) {
            // Another scheduler node may have inserted the unique key between lookup and insert.
            return checkpointService.getOrCreateRun(idempotencyKey);
        }
    }

    private boolean executeStage(Long runId, String executionToken, FootballWorkflowStage stage, BooleanSupplier stop,
                                 Supplier<String> operation, List<WorkflowStep> steps) {
        if (stage != FootballWorkflowStage.DATA_READINESS && checkpointService.isStageCompleted(runId, stage)) {
            steps.add(new WorkflowStep(stage.name(), "SKIPPED", 0, "Previously completed checkpoint reused."));
            return true;
        }
        if (stop.getAsBoolean()) return false;

        long started = System.nanoTime();
        try {
            checkpointService.startStage(runId, stage, executionToken);
            log.info("Football workflow stage STARTED: {}", stage);
            String message = operation.get();
            checkpointService.completeStage(runId, stage, executionToken);
            steps.add(step(stage, "COMPLETED", started, message));
            log.info("Football workflow stage COMPLETED: {} durationMs={}", stage,
                    Math.max(0, (System.nanoTime() - started) / 1_000_000));
            return true;
        } catch (RuntimeException exception) {
            String reason = describe(exception);
            if (stop.getAsBoolean()) {
                steps.add(step(stage, "INTERRUPTED", started, reason));
                checkpointService.releaseRun(runId);
                log.info("Football workflow stage interrupted: {}", stage);
                return false;
            }
            checkpointService.failStage(runId, stage, reason, executionToken);
            steps.add(step(stage, "FAILED", started, reason));
            log.error("Football workflow stage FAILED: {}", stage, exception);
            return false;
        }
    }

    private WorkflowStep step(FootballWorkflowStage stage, String status, long startedNanos, String message) {
        return new WorkflowStep(stage.name(), status,
                Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000), message);
    }

    private String stageStatus(BooleanSupplier stop, List<WorkflowStep> steps) {
        if (stop.getAsBoolean()) return "INTERRUPTED";
        return steps.stream().anyMatch(step -> "FAILED".equals(step.status())) ? "FAILED" : "INTERRUPTED";
    }

    private WorkflowReport report(String status, LocalDate utcDate, Instant startedAt,
                                  List<WorkflowStep> steps, Long contentRunId,
                                  int draftsGenerated, int contentFailures) {
        return new WorkflowReport(status, utcDate, startedAt, Instant.now(), steps,
                contentRunId, draftsGenerated, contentFailures);
    }

    private String describe(RuntimeException exception) {
        String message = exception.getMessage();
        return exception.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }
}
