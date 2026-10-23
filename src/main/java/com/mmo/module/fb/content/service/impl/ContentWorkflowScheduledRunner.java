package com.mmo.module.fb.content.service.impl;

import com.mmo.module.fb.content.service.ContentDraftService.DraftFormat;
import com.mmo.module.fb.content.service.ContentLifecycleService;
import com.mmo.module.fb.content.service.ContentWorkflowOrchestrator.WorkflowCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Starts the content-draft stage after the existing fixture-sync and prediction jobs.
 * Draft generation is automated; editorial approval remains an explicit human gate.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.content.workflow.scheduler", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class ContentWorkflowScheduledRunner {

    private final ContentLifecycleService contentLifecycleService;

    @Scheduled(cron = "${app.content.workflow.scheduler.cron:0 30 11 * * *}", zone = "UTC")
    public void generateUpcomingMatchDrafts() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant from = today.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = today.plusDays(15).atStartOfDay().toInstant(ZoneOffset.UTC);
        WorkflowCommand command = new WorkflowCommand(from, to, null, 20, DraftFormat.PRE_MATCH);
        String idempotencyKey = "scheduled-content-" + today;

        try {
            var run = contentLifecycleService.generate(command, idempotencyKey);
            log.info("Scheduled content workflow runId={} status={} generated={} failures={}",
                    run.getId(), run.getStatus(), run.getDraftsGenerated(), run.getFailures());
        } catch (RuntimeException failure) {
            log.error("Scheduled content workflow failed for {}: {}", today, safeMessage(failure), failure);
        }
    }

    private static String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return failure.getClass().getSimpleName();
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
