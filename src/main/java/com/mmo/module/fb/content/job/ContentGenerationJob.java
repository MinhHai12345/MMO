package com.mmo.module.fb.content.job;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.job.AbstractJob;
import com.mmo.module.fb.content.service.ContentDraftService.DraftFormat;
import com.mmo.module.fb.content.service.ContentLifecycleService;
import com.mmo.module.fb.content.service.ContentWorkflowOrchestrator.WorkflowCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/** Scheduled content-generation entry point. It does not orchestrate other Quartz jobs. */
@Slf4j
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class ContentGenerationJob extends AbstractJob<CronJob> {

    private final ContentLifecycleService contentLifecycleService;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant from = today.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = today.plusDays(15).atStartOfDay().toInstant(ZoneOffset.UTC);
        WorkflowCommand command = new WorkflowCommand(from, to, null, 20, DraftFormat.PRE_MATCH);
        String idempotencyKey = "scheduled-content-" + today;

        log.info("Starting scheduled PRE_MATCH content generation for {}", today);
        var run = contentLifecycleService.generate(command, idempotencyKey);
        log.info("Scheduled content generation completed: date={} runId={} status={} generated={} failures={}",
                today, run.getId(), run.getStatus(), run.getDraftsGenerated(), run.getFailures());
    }
}
