package com.mmo.module.fb.config;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.entity.JobState;
import com.mmo.core.cronjob.repository.CronJobRepository;
import com.mmo.core.cronjob.service.CronJobService;
import com.mmo.core.initialize.DataInitializer;
import com.mmo.module.fb.analytics.job.AdvancedMetricsJob;
import com.mmo.module.fb.analytics.job.MatchProcessPredictionJob;
import com.mmo.module.fb.analytics.job.PredictionEvaluationJob;
import com.mmo.module.fb.analytics.job.WalkForwardBackTestJob;
import com.mmo.module.fb.ingest.job.FootballDataBootstrapJob;
import com.mmo.module.fb.ingest.job.LeagueSyncJob;
import com.mmo.module.fb.ingest.job.MatchFixtureSyncJob;
import com.mmo.module.fb.ingest.job.MatchResultSyncJob;
import com.mmo.module.fb.ingest.job.MatchStatisticsSyncJob;
import com.mmo.module.fb.ingest.job.UnderStatHistoricalStatsJob;
import com.mmo.module.fb.ingest.job.UnderStatCatalogSyncJob;
import com.mmo.module.fb.ingest.job.TeamSyncJob;
import com.mmo.module.fb.monetization.job.SubscriptionExpiryCheckJob;
import com.mmo.module.fb.workflow.job.FootballWorkflowOrchestrationJob;
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

        createOrUpdateJob(FootballWorkflowOrchestrationJob.class,
                "0 30 11 * * ?", "End-to-end workflow: refresh data, calculate analytics and generate PRE_MATCH drafts (daily UTC 11:30)");

        // Bootstrap may backfill multiple seasons. Keep it out of the frequent production path.
        createOrUpdateJob(FootballDataBootstrapJob.class,
                "0 0 1 1 * ?", "Bootstrap configured leagues, teams, fixtures and standings (monthly UTC 01:00)");
        createOrUpdateJob(LeagueSyncJob.class,
                "0 0 2 1 * ?", "Refresh league and season metadata (monthly UTC 02:00)");
        createOrUpdateJob(TeamSyncJob.class,
                "0 0 3 1,15 * ?", "Refresh team metadata (UTC 03:00 on days 1 and 15)");

        // Match data has direct value to content freshness; use bounded periodic refreshes.
        createOrUpdateJob(MatchResultSyncJob.class,
                "0 0 0/4 * * ?", "Refresh recent results and statuses every 4 hours");
        createOrUpdateJob(MatchFixtureSyncJob.class,
                "0 0 0/6 * * ?", "Refresh upcoming fixtures every 6 hours");
        createOrUpdateJob(MatchStatisticsSyncJob.class,
                "0 0 0/6 * * ?", "Refresh finished-match statistics every 6 hours");
        createOrUpdateJob(UnderStatCatalogSyncJob.class,
                "0 5 0 * * ?", "Discover and upsert all Understat leagues, teams and players daily at 00:05 UTC");
        createOrUpdateJob(UnderStatHistoricalStatsJob.class,
                "0 30 0/12 * * ?", "Enrich active seasons with Understat xG, PPDA and team history every 12 hours");

        createOrUpdateJob(MatchProcessPredictionJob.class,
                "0 0 0/3 * * ?", "Recalculate eligible upcoming-match predictions every 3 hours");
        createOrUpdateJob(AdvancedMetricsJob.class,
                "0 0 0/6 * * ?", "Recalculate team form and head-to-head metrics every 6 hours");
        createOrUpdateJob(PredictionEvaluationJob.class,
                "0 0/30 * * * ?", "Evaluate predictions against newly completed match results every 30 minutes");

        // Walk-forward evaluation is computationally expensive and is not required for every content run.
        createOrUpdateJob(WalkForwardBackTestJob.class,
                "0 15 6 ? * SUN", "Run the 180-day walk-forward evaluation weekly on Sunday at 06:15 UTC");

        createOrUpdateJob(SubscriptionExpiryCheckJob.class,
                "0 0 0 * * ?", "Expire subscriptions and identify renewals daily at 00:00 UTC");

        // Polling is cheap, but the worker itself must enforce opt-in, state and retry limits.
        createOrUpdateJob(PublicationWorkerQuartzJob.class,
                "0 * * * * ?", "Poll approved, due Telegram publications once per minute; opt-in required");

        // These legacy rows may already exist in production DBs. Merely omitting them
        // from registration would leave their persisted Quartz triggers active.
        pauseRetiredJob("MatchStatusTrackerJob");
        pauseRetiredJob("CrawlerHealthCheckJob");
        pauseRetiredJob("DataCleanupJob");
        pauseRetiredJob("MatchLineupOddsUpdateJob");

        log.info("Football CronJob initialization completed.");
    }

    /**
     * Keep persisted schedules aligned with code. The old create-only behavior meant
     * schedule fixes were silently ignored for installations that already had rows.
     */
    private void pauseRetiredJob(String jobName) {
        cronJobRepository.findByJobNameAndJobGroup(jobName, FB_JOB_GROUP).ifPresent(job -> {
            if (job.getJobStatus() != JobState.PAUSED) {
                cronJobService.pauseJob(job.getId());
                log.warn("Paused retired CronJob [{}]; implementation is empty or not part of the content MVP", jobName);
            }
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

        if (!changed) {
            return;
        }

        job.setJobClass(jobClass.getName());
        job.setCronExpression(cronExpression);
        job.setDescription(description);
        cronJobService.saveJob(job);
        log.info("{} CronJob [{}] with expression {}",
                isNew ? "Created" : "Updated", jobName, cronExpression);
    }
}
