package com.mmo.module.fb.config;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.repository.CronJobRepository;
import com.mmo.core.cronjob.service.CronJobService;
import com.mmo.core.initialize.DataInitializer;
import com.mmo.module.fb.analytics.job.AdvancedMetricsJob;
import com.mmo.module.fb.analytics.job.MatchProcessPredictionJob;
import com.mmo.module.fb.analytics.job.PredictionEvaluationJob;
import com.mmo.module.fb.analytics.job.WalkForwardBackTestJob;
import com.mmo.module.fb.ingest.job.FootballDataBootstrapJob;
import com.mmo.module.fb.ingest.job.FootballDataSyncJob;
import com.mmo.module.fb.ingest.job.MatchEnrichmentJob;
import com.mmo.module.fb.monetization.job.SubscriptionExpiryCheckJob;
import com.mmo.module.fb.content.job.ContentGenerationJob;
import com.mmo.module.fb.workflow.job.PublicationWorkerQuartzJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FBCronjobInitial implements DataInitializer {
    private static final String FB_JOB_GROUP = "FBJobGroup";

    private final CronJobRepository cronJobRepository;
    private final CronJobService cronJobService;

    @Override
    public void initialize() {
        log.info("Initializing Football Analytics CronJobs (UTC timezone)...");

        deleteLegacyJob("FootballWorkflowOrchestrationJob");
        createOrUpdateJob(ContentGenerationJob.class,
                "0 30 11 * * ?", "Generate PRE_MATCH content drafts daily at 11:30 UTC");

        // The first execution performs the historical backfill. Once its durable provider
        // checkpoints exist, the same job automatically switches to current-season-only sync.
        createOrUpdateJob(FootballDataBootstrapJob.class,
                "0 0 1 * * ?", "Initial historical Core backfill, then current-season Core sync daily at 01:00 UTC");
        createOrUpdateJob(FootballDataSyncJob.class,
                "0 0 0/6 * * ?", "Incrementally synchronize canonical football data every 6 hours");
        createOrUpdateJob(MatchEnrichmentJob.class,
                "0 30 0/12 * * ?", "Enrich finished matches with historical and player statistics every 12 hours");

        // Analytics and evaluation jobs own their own schedules. The content job never
        // triggers already-scheduled jobs, avoiding duplicate executions.
        createOrUpdateJob(MatchProcessPredictionJob.class,
                "0 0 0/3 * * ?", "Recalculate eligible upcoming-match predictions every 3 hours");
        createOrUpdateJob(AdvancedMetricsJob.class,
                "0 0 0/6 * * ?", "Recalculate team form and head-to-head metrics every 6 hours");
        createOrUpdateJob(PredictionEvaluationJob.class,
                "0 0/30 * * * ?", "Evaluate predictions against newly completed match results every 30 minutes");
        createOrUpdateJob(WalkForwardBackTestJob.class,
                "0 15 6 ? * SUN", "Run the 180-day walk-forward evaluation weekly on Sunday at 06:15 UTC");
        createOrUpdateJob(SubscriptionExpiryCheckJob.class,
                "0 0 0 * * ?", "Expire subscriptions and identify renewals daily at 00:00 UTC");
        createOrUpdateJob(PublicationWorkerQuartzJob.class,
                "0 * * * * ?", "Poll approved, due Telegram publications once per minute; opt-in required");

        // Remove obsolete persisted schedules as well as their source classes. Leaving
        // paused registry rows creates a false operational inventory and permits accidental resume.
        deleteLegacyJob("LeagueSyncJob");
        deleteLegacyJob("TeamSyncJob");
        deleteLegacyJob("MatchFixtureSyncJob");
        deleteLegacyJob("MatchResultSyncJob");
        deleteLegacyJob("MatchStatisticsSyncJob");
        deleteLegacyJob("FootballDataUkCoreSyncJob");
        deleteLegacyJob("UnderStatHistoricalStatsJob");
        deleteLegacyJob("UnderStatCatalogSyncJob");
        deleteLegacyJob("MatchStatusTrackerJob");
        deleteLegacyJob("CrawlerHealthCheckJob");
        deleteLegacyJob("DataCleanupJob");
        deleteLegacyJob("MatchLineupOddsUpdateJob");

        log.info("Football CronJob initialization completed.");
    }

    private void deleteLegacyJob(String jobName) {
        cronJobRepository.findByJobNameAndJobGroup(jobName, FB_JOB_GROUP).ifPresent(job -> {
            cronJobService.deleteJob(job.getId());
            log.warn("Deleted obsolete CronJob [{}] from Quartz and cron_jobs registry", jobName);
        });
    }

    private void createOrUpdateJob(Class<?> jobClass, String cronExpression, String description) {
        String jobName = jobClass.getSimpleName();
        CronJob job = cronJobRepository.findByJobNameAndJobGroup(jobName, FB_JOB_GROUP)
                .orElseGet(() -> {
                    CronJob created = new CronJob();
                    created.setJobName(jobName);
                    created.setJobGroup(FB_JOB_GROUP);
                    return created;
                });

        boolean isNew = job.getId() == null;
        boolean changed = isNew
                || !jobClass.getName().equals(job.getJobClass())
                || !cronExpression.equals(job.getCronExpression())
                || !description.equals(job.getDescription());

        if (!changed) return;

        job.setJobClass(jobClass.getName());
        job.setCronExpression(cronExpression);
        job.setDescription(description);
        cronJobService.saveJob(job);
        log.info("{} CronJob [{}] with expression {}",
                isNew ? "Created" : "Updated", jobName, cronExpression);
    }
}
