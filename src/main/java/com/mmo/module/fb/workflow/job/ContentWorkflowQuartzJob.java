package com.mmo.module.fb.workflow.job;

import com.mmo.module.fb.content.service.ContentDraftService.DraftFormat;
import com.mmo.module.fb.content.service.ContentLifecycleService;
import com.mmo.module.fb.content.service.ContentWorkflowOrchestrator.WorkflowCommand;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.PersistJobDataAfterExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Quartz entry point for the content stage. Data ingestion remains owned by the
 * existing ingestion Quartz jobs; this job consumes the persisted, refreshed data.
 */
@Slf4j
@DisallowConcurrentExecution
@PersistJobDataAfterExecution
public class ContentWorkflowQuartzJob extends QuartzJobBean {

    @Autowired
    private ContentLifecycleService contentLifecycleService;

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant from = today.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = today.plusDays(15).atStartOfDay().toInstant(ZoneOffset.UTC);
        WorkflowCommand command = new WorkflowCommand(from, to, null, 20, DraftFormat.PRE_MATCH);
        String idempotencyKey = "scheduled-content-" + today;

        try {
            var run = contentLifecycleService.generate(command, idempotencyKey);
            log.info("Quartz content workflow runId={} status={} generated={} failures={}",
                    run.getId(), run.getStatus(), run.getDraftsGenerated(), run.getFailures());
        } catch (RuntimeException failure) {
            log.error("Quartz content workflow failed for {}: {}", today, safeMessage(failure), failure);
            throw new JobExecutionException("Content workflow failed for " + today, failure, false);
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
