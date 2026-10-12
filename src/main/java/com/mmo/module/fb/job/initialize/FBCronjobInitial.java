package com.mmo.module.fb.job.initialize;

import com.mmo.cronjob.entity.CronJob;
import com.mmo.cronjob.repository.CronJobRepository;
import com.mmo.cronjob.service.CronJobService;
import com.mmo.initialize.DataInitializer;
import com.mmo.module.fb.job.CrawlerHealthCheckJob;
import com.mmo.module.fb.job.DataCleanupJob;
import com.mmo.module.fb.job.LeagueSyncJob;
import com.mmo.module.fb.job.MatchDailyRecapJob;
import com.mmo.module.fb.job.MatchDashboardJob;
import com.mmo.module.fb.job.MatchFixtureSyncJob;
import com.mmo.module.fb.job.MatchInsightJob;
import com.mmo.module.fb.job.MatchLineupOddsUpdateJob;
import com.mmo.module.fb.job.MatchProcessPredictionJob;
import com.mmo.module.fb.job.MatchResultSyncJob;
import com.mmo.module.fb.job.MatchSeasonFixtureSyncJob;
import com.mmo.module.fb.job.MatchStatusTrackerJob;
import com.mmo.module.fb.job.NotificationPublisherJob;
import com.mmo.module.fb.job.TeamSyncJob;
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
        log.info("Initializing Football Analytics CronJobs (UTC Timezone)...");

        // =========================================================================
        // GROUP 1: DYNAMIC SEASON SYNC (Lịch thi đấu & Cấu trúc giải)
        // =========================================================================
        createJobIfNotExist(LeagueSyncJob.class.getSimpleName(), LeagueSyncJob.class,
                "0 0 2 1 * ?",
                "Sync League and Season info (UTC 02:00 / VN 09:00 AM - 1st of month)"
        );
        createJobIfNotExist(TeamSyncJob.class.getSimpleName(), TeamSyncJob.class,
                "0 0 3 1,15 * ?",
                "Sync Team metadata (UTC 03:00 / VN 10:00 AM - 1st & 15th of month)"
        );
        createJobIfNotExist(MatchSeasonFixtureSyncJob.class.getSimpleName(), MatchSeasonFixtureSyncJob.class,
                "0 0 1 1 * ?",
                "Sync full season fixtures frame (UTC 01:00 / VN 08:00 AM - 1st of month)"
        );

        // =========================================================================
        // GROUP 2: DAILY DATA PIPELINE (Luồng xử lý dữ liệu hàng ngày)
        // =========================================================================
        createJobIfNotExist(MatchResultSyncJob.class.getSimpleName(), MatchResultSyncJob.class,
                "0 0 4 * * ?",
                "Fetch yesterday's match results and actual xG (UTC 04:00 / VN 11:00 AM)"
        );
        createJobIfNotExist(MatchDailyRecapJob.class.getSimpleName(), MatchDailyRecapJob.class,
                "0 0 5 * * ?",
                "Calculate yesterday's prediction accuracy & ROI (UTC 05:00 / VN 12:00 PM)"
        );
        createJobIfNotExist(MatchFixtureSyncJob.class.getSimpleName(), MatchFixtureSyncJob.class,
                "0 30 5 * * ?",
                "Fetch today & tomorrow fixtures update (UTC 05:30 / VN 12:30 PM)"
        );
        createJobIfNotExist(MatchInsightJob.class.getSimpleName(), MatchInsightJob.class,
                "0 0/30 6-9 * * ?",
                "Fetch H2H, Form, Rest Days & Injuries (Every 30m UTC 06:00-09:00 / VN 13:00-16:00)"
        );
        createJobIfNotExist(MatchProcessPredictionJob.class.getSimpleName(), MatchProcessPredictionJob.class,
                "0 0 10 * * ?",
                "Calculate expected xG, Win Probs & Confidence Score (UTC 10:00 / VN 17:00 PM)"
        );
        createJobIfNotExist(MatchDashboardJob.class.getSimpleName(), MatchDashboardJob.class,
                "0 30 10 * * ?",
                "Render Thymeleaf templates & enqueue to NotificationQueue (UTC 10:30 / VN 17:30 PM)"
        );

        // =========================================================================
        // GROUP 3: REALTIME & SÁT GIỜ BÓNG LĂN
        // =========================================================================
        createJobIfNotExist(NotificationPublisherJob.class.getSimpleName(), NotificationPublisherJob.class,
                "0 */2 * * * ?",
                "Scan queue and publish messages to Telegram/Facebook/X (Every 2 mins)"
        );
        createJobIfNotExist(MatchStatusTrackerJob.class.getSimpleName(), MatchStatusTrackerJob.class,
                "0 0 */3 * * ?",
                "Track postponed/cancelled matches (Every 3 hours)"
        );
        createJobIfNotExist(MatchLineupOddsUpdateJob.class.getSimpleName(), MatchLineupOddsUpdateJob.class,
                "0 0/15 15-21 * * ?",
                "Update official lineups & odds movement before match (Every 15m UTC 15:00-21:00)"
        );

        // =========================================================================
        // GROUP 4: MONITORING & SYSTEM MAINTENANCE
        // =========================================================================
        createJobIfNotExist(CrawlerHealthCheckJob.class.getSimpleName(), CrawlerHealthCheckJob.class,
                "0 30 9 * * ?",
                "Test crawlers DOM/Selectors health check (UTC 09:30 / VN 16:30 PM)"
        );
        createJobIfNotExist(DataCleanupJob.class.getSimpleName(), DataCleanupJob.class,
                "0 0 1 ? * SUN",
                "Cleanup temp Playwright files and old logs (UTC 01:00 Sun / VN 08:00 AM Sun)"
        );

        log.info("CronJobs initialization completed successfully.");
    }

    private void createJobIfNotExist(String jobName, Class<?> jobClass, String cronExpression, String description) {
        if (cronJobRepository.findByJobNameAndJobGroup(jobName, FB_JOB_GROUP).isEmpty()) {
            final CronJob job = new CronJob();
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
