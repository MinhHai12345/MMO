package com.mmo.module.fb.workflow.job;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.job.AbstractJob;
import com.mmo.module.fb.workflow.application.port.in.FootballWorkflowUseCase;
import com.mmo.module.fb.workflow.application.port.in.FootballWorkflowUseCase.WorkflowReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Quartz adapter for the durable end-to-end football workflow.
 *
 * <p>The coordinator owns stage ordering, checkpoints, retry and resume semantics.
 * This adapter only forwards Quartz interruption and reports the execution outcome.</p>
 */
@Slf4j
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class FootballWorkflowOrchestrationQuartzJob extends AbstractJob<CronJob> {

    private final FootballWorkflowUseCase footballWorkflowUseCase;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        LocalDate workflowDate = LocalDate.now(ZoneOffset.UTC);
        log.info("Starting football workflow orchestration: workflowDate={}", workflowDate);

        WorkflowReport report = footballWorkflowUseCase.run(workflowDate, this::shouldStop);

        log.info("Football workflow orchestration finished: workflowDate={} status={} stages={} "
                        + "contentRunId={} draftsGenerated={} contentFailures={}",
                workflowDate, report.status(), report.steps().size(), report.contentRunId(),
                report.draftsGenerated(), report.contentFailures());

        if ("FAILED".equalsIgnoreCase(report.status())) {
            throw new IllegalStateException("Football workflow failed for " + workflowDate
                    + "; inspect durable workflow stage checkpoints for the failure reason.");
        }
    }
}
