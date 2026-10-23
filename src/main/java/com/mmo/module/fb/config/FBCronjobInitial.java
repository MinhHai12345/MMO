package com.mmo.module.fb.config;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.repository.CronJobRepository;
import com.mmo.core.cronjob.service.CronJobService;
import com.mmo.core.initialize.DataInitializer;
import com.mmo.module.fb.analytics.job.AdvancedMetricsJob;
import com.mmo.module.fb.analytics.job.MatchProcessPredictionJob;
import com.mmo.module.fb.analytics.job.PredictionEvaluationJob;
import com.mmo.module.fb.analytics.job.WalkForwardBackTestJob;
import com.mmo.module.fb.ingest.job.CrawlerHealthCheckJob;
import com.mmo.module.fb.ingest.job.DataCleanupJob;
import com.mmo.module.fb.ingest.job.FootballDataBootstrapJob;
import com.mmo.module.fb.ingest.job.LeagueSyncJob;
import com.mmo.module.fb.ingest.job.MatchFixtureSyncJob;
import com.mmo.module.fb.ingest.job.MatchLineupOddsUpdateJob;
import com.mmo.module.fb.ingest.job.MatchResultSyncJob;
import com.mmo.module.fb.ingest.job.MatchStatisticsSyncJob;
import com.mmo.module.fb.ingest.job.MatchStatusTrackerJob;
import com.mmo.module.fb.ingest.job.TeamSyncJob;
import com.mmo.module.fb.monetization.job.SubscriptionExpiryCheckJob;
import com.mmo.module.fb.workflow.job.FootballWorkflowOrchestrationJob;
import com.mmo.module.fb.workflow.job.PublicationWorkerQuartzJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FBCronjobInitial implements DataInitializer {
    private static final String FB_JOB_GROUP = "FBJobGroup";
    private static final String DISABLED_CRON_EXPRESSION = "0 0 0 1 1 ?";
    private static final String PUBLICATION_WORKER_CRON_EXPRESSION = "0/30 * * * * ?";

    private final CronJobRepository cronJobRepository;
    private final CronJobService cronJobService;

    @Value("${app.content.publishing.worker.enabled:false}")
    private boolean publicationWorkerEnabled;

    @Override
    public void initialize() {
        log.info("Initializing Football Analytics CronJobs (UTC Timezone)...");
        createJobIfNotExist(
                FootballWorkflowOrchestrationJob.class.getSimpleName(),
                FootballWorkflowOrchestrationJob.class,
                "0 30 11 * * ?",
                "End-to-end workflow: run ingestion and analytics jobs in sequence, then generate PRE_MATCH drafts (UTC 11:30)"
        );

        createJobIfNotExist(
                FootballDataBootstrapJob.class.getSimpleName(),
                FootballDataBootstrapJob.class,
                "0 0 1 1 * ?",
                "Bootstrap leagues, teams, fixtures, scores and standings for configured seasons; "
                        + "FOOTBALL_DATA_SEASONS enables historical backfill (UTC 01:00 / VN 08:00 AM - 1st of month)"
        );

        createJobIfNotExist(LeagueSyncJob.class.getSimpleName(), LeagueSyncJob.class,
                "0 0 2 1 * ?", "Sync League and Season info (UTC 02:00 / VN 09:00 AM - 1st of month)");
        createJobIfNotExist(TeamSyncJob.class.getSimpleName(), TeamSyncJob.class,
                "0 0 3 1,15 * ?", "Sync Team metadata (UTC 03:00 / VN 10:00 AM - 1st & 15th of month)");

        createJobIfNotExist(MatchResultSyncJob.class.getSimpleName(), MatchResultSyncJob.class,
                "0 0 4 * * ?", "Refresh match statuses and scores from the previous 7 days through today (UTC 04:00 / VN 11:00 AM)");
        createJobIfNotExist(MatchStatisticsSyncJob.class.getSimpleName(), MatchStatisticsSyncJob.class,
                "0 0 */4 * * ?", "Fetch real SofaScore statistics for finished matches (every 4 hours UTC)");
        createJobIfNotExist(PredictionEvaluationJob.class.getSimpleName(), PredictionEvaluationJob.class,
                "0 0/30 * * * ?", "Evaluate completed predictions using actual results and normalized Brier quality (every 30 minutes)");
        createJobIfNotExist(WalkForwardBackTestJob.class.getSimpleName(), WalkForwardBackTestJob.class,
                "0 15 6 * * ?", "Run 180-day leakage-safe walk-forward backtest and probability calibration (daily UTC 06:15)");
        createJobIfNotExist(MatchFixtureSyncJob.class.getSimpleName(), MatchFixtureSyncJob.class,
                "0 30 5 * * ?", "Refresh fixtures from today through the next 14 days (UTC 05:30 / VN 12:30 PM)");
        createJobIfNotExist(MatchProcessPredictionJob.class.getSimpleName(), MatchProcessPredictionJob.class,
                "0 0 10 * * ?", "Calculate expected xG, Win Probs & Confidence Score (UTC 10:00 / VN 17:00 PM)");

        createJobIfNotExist(MatchStatusTrackerJob.class.getSimpleName(), MatchStatusTrackerJob.class,
                "0 0 */3 * * ?", "Track postponed/cancelled matches (Every 3 hours)");
        createJobIfNotExist(MatchLineupOddsUpdateJob.class.getSimpleName(), MatchLineupOddsUpdateJob.class,
                "0 0/15 15-21 * * ?", "Update official lineups & odds movement before match (Every 15m UTC 15:00-21:00)");

        createJobIfNotExist(CrawlerHealthCheckJob.class.getSimpleName(), CrawlerHealthCheckJob.class,
                "0 30 9 * * ?", "Test crawlers DOM/Selectors health check (UTC 09:30 / VN 16:30 PM)");
        createJobIfNotExist(DataCleanupJob.class.getSimpleName(), DataCleanupJob.class,
                "0 0 1 ? * SUN", "Cleanup temp Playwright files and old logs (UTC 01:00 Sun / VN 08:00 AM Sun)");

        createJobIfNotExist(AdvancedMetricsJob.class.getSimpleName(), AdvancedMetricsJob.class,
                "0 */2 * * * ?", "Calculate PPDA, Field Tilt, xG Chain and store advanced metrics (Every 2 hours)");

        createJobIfNotExist(SubscriptionExpiryCheckJob.class.getSimpleName(), SubscriptionExpiryCheckJob.class,
                "0 0 0 * * ?", "Check subscription expiry and send warnings (Daily at midnight UTC)");

        configurePublicationWorker();
        log.info("CronJobs initialization completed successfully.");
    }

    private void configurePublicationWorker() {
        String jobName = PublicationWorkerQuartzJob.class.getSimpleName();
        String cronExpression = publicationWorkerEnabled
                ? PUBLICATION_WORKER_CRON_EXPRESSION
                : DISABLED_CRON_EXPRESSION;
        String description = "Poll approved, due Telegram publications; opt-in via CONTENT_PUBLISHING_WORKER_ENABLED";

        cronJobRepository.findByJobNameAndJobGroup(jobName, FB_JOB_GROUP)
                .ifPresentOrElse(
                        job -> updatePublicationWorkerSchedule(job, cronExpression),
                        () -> createJobIfNotExist(
                                jobName,
                                PublicationWorkerQuartzJob.class,
                                cronExpression,
                                description
                        )
                );
    }

    private void updatePublicationWorkerSchedule(CronJob job, String cronExpression) {
        if (cronExpression.equals(job.getCronExpression())) {
            return;
        }

        job.setCronExpression(cronExpression);
        cronJobService.updateScheduleJob(job);
        log.info(
                "Updated CronJob [{}] schedule to [{}] (enabled={})",
                job.getJobName(),
                cronExpression,
                publicationWorkerEnabled
        );
    }

    private void createJobIfNotExist(String jobName, Class<?> jobClass, String cronExpression, String description) {
        if (cronJobRepository.findByJobNameAndJobGroup(jobName, FB_JOB_GROUP).isEmpty()) {
            CronJob job = new CronJob();
            job.setJobName(jobName);
            job.setJobGroup(FB_JOB_GROUP);
            job.setJobClass(jobClass.getName());
            job.setCronExpression(cronExpression);
            job.setDescription(description);

            cronJobService.saveJob(job);
            log.info("Created CronJob [{}] with expression: {}", jobName, cronExpression);
        }
    }
}
