package com.mmo.module.fb.workflow.job;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.entity.JobCompleteState;
import com.mmo.core.cronjob.service.CronJobService;
import com.mmo.module.fb.content.service.ContentDraftService.DraftFormat;
import com.mmo.module.fb.content.service.ContentLifecycleService;
import com.mmo.module.fb.content.service.ContentWorkflowOrchestrator.WorkflowCommand;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.JobKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Coordinates the end-to-end FB workflow by triggering the project's persisted CronJob
 * definitions and waiting for each job's listener-persisted completion state before
 * advancing to the next dependent stage.
 */
@Slf4j
@Component
@DisallowConcurrentExecution
public class FootballWorkflowOrchestrationJob extends QuartzJobBean {

    private static final String JOB_GROUP = "FBJobGroup";
    private static final long POLL_INTERVAL_MS = 1_000L;

    @Autowired
    private CronJobService cronJobService;

    @Autowired
    private Scheduler scheduler;

    @Autowired
    private ContentLifecycleService contentLifecycleService;

    @Value("${app.fb.workflow.job-timeout-ms:1800000}")
    private long jobTimeoutMs;

    @Override
    protected void executeInternal(JobExecutionContext context) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        log.info("Starting FB end-to-end workflow for {}", today);

        // Reuse the existing persisted jobs; ordering here is explicit and completion-gated.
        boolean bootstrapExecuted = runBootstrapIfNeeded();
        if (!bootstrapExecuted) {
            runExistingJobIfStale("LeagueSyncJob", Duration.ofDays(30));
            runExistingJobIfStale("TeamSyncJob", Duration.ofDays(15));
        }
        runExistingJob("MatchResultSyncJob");
        runExistingJob("MatchFixtureSyncJob");
        runExistingJob("MatchStatisticsSyncJob");
        runExistingJob("AdvancedMetricsJob");
        runExistingJob("MatchProcessPredictionJob");

        Instant from = today.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = today.plusDays(15).atStartOfDay().toInstant(ZoneOffset.UTC);
        WorkflowCommand command = new WorkflowCommand(from, to, null, 20, DraftFormat.PRE_MATCH);
        String idempotencyKey = "scheduled-content-" + today;
        var run = contentLifecycleService.generate(command, idempotencyKey);

        log.info("FB end-to-end workflow completed for {}: runId={} status={} generated={} failures={}",
                today, run.getId(), run.getStatus(), run.getDraftsGenerated(), run.getFailures());
    }

    /**
     * Bootstrap can backfill configured historical seasons, so do not repeat it every day.
     * Re-run it when it has never succeeded, failed last time, or is at least 30 days old.
     */
    private boolean runBootstrapIfNeeded() {
        waitUntilNotRunning(new JobKey("FootballDataBootstrapJob", JOB_GROUP), "FootballDataBootstrapJob");
        CronJob bootstrap = cronJobService.getJob("FootballDataBootstrapJob", JOB_GROUP);
        ZonedDateTime lastExecution = bootstrap.getLastExecutedTime();
        boolean recentlySucceeded = bootstrap.getLastCompleteStatus() == JobCompleteState.SUCCESS
                && lastExecution != null
                && lastExecution.isAfter(ZonedDateTime.now(ZoneOffset.UTC).minus(Duration.ofDays(30)));

        if (recentlySucceeded) {
            log.info("Workflow stage SKIPPED job=FootballDataBootstrapJob reason=recent_success lastExecutedTime={}",
                    lastExecution);
            return false;
        }
        runExistingJob("FootballDataBootstrapJob");
        return true;
    }

    private void runExistingJobIfStale(String jobName, Duration maxAge) {
        waitUntilNotRunning(new JobKey(jobName, JOB_GROUP), jobName);
        CronJob job = cronJobService.getJob(jobName, JOB_GROUP);
        ZonedDateTime lastExecution = job.getLastExecutedTime();
        boolean recentlySucceeded = job.getLastCompleteStatus() == JobCompleteState.SUCCESS
                && lastExecution != null
                && lastExecution.isAfter(ZonedDateTime.now(ZoneOffset.UTC).minus(maxAge));
        if (recentlySucceeded) {
            log.info("Workflow stage SKIPPED job={} reason=recent_success lastExecutedTime={}",
                    jobName, lastExecution);
            return;
        }
        runExistingJob(jobName);
    }

    private void runExistingJob(String jobName) {
        CronJob definition = cronJobService.getJob(jobName, JOB_GROUP);
        JobKey jobKey = new JobKey(definition.getJobName(), definition.getJobGroup());

        waitUntilNotRunning(jobKey, jobName);
        ZonedDateTime previousExecution = cronJobService.getJob(jobName, JOB_GROUP).getLastExecutedTime();

        log.info("Workflow stage STARTED job={}", jobName);
        cronJobService.startJobNow(definition.getId());
        long deadline = System.currentTimeMillis() + Math.max(POLL_INTERVAL_MS, jobTimeoutMs);

        while (System.currentTimeMillis() < deadline) {
            CronJob current = cronJobService.getJob(jobName, JOB_GROUP);
            ZonedDateTime lastExecution = current.getLastExecutedTime();
            boolean executionRecorded = lastExecution != null
                    && (previousExecution == null || lastExecution.isAfter(previousExecution))
                    && current.getLastCompleteStatus() != null;

            if (executionRecorded) {
                // The execution listener persists the completion timestamp before Quartz fully
                // releases the execution slot. Wait for that slot and re-read the final state.
                waitUntilNotRunning(jobKey, jobName);
                CronJob completed = cronJobService.getJob(jobName, JOB_GROUP);
                if (completed.getLastCompleteStatus() != JobCompleteState.SUCCESS) {
                    throw new IllegalStateException("Workflow stage failed: " + jobName
                            + " completed with status " + completed.getLastCompleteStatus());
                }
                log.info("Workflow stage COMPLETED job={} lastExecutedTime={}",
                        jobName, completed.getLastExecutedTime());
                return;
            }

            sleep(jobName);
        }

        throw new IllegalStateException("Timed out waiting for existing CronJob " + jobName
                + " after " + jobTimeoutMs + " ms");
    }

    private void waitUntilNotRunning(JobKey jobKey, String jobName) {
        long deadline = System.currentTimeMillis() + Math.max(POLL_INTERVAL_MS, jobTimeoutMs);
        while (System.currentTimeMillis() < deadline) {
            try {
                boolean running = scheduler.getCurrentlyExecutingJobs().stream()
                        .anyMatch(execution -> execution.getJobDetail().getKey().equals(jobKey));
                if (!running) {
                    return;
                }
            } catch (SchedulerException exception) {
                throw new IllegalStateException("Cannot inspect running Quartz jobs before " + jobName, exception);
            }
            sleep(jobName);
        }
        throw new IllegalStateException("Timed out waiting for existing CronJob to become idle: " + jobName);
    }

    private void sleep(String jobName) {
        try {
            Thread.sleep(POLL_INTERVAL_MS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for CronJob " + jobName, exception);
        }
    }
}
