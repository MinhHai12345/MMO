package com.mmo.module.fb.job.initialize;

import com.mmo.cronjob.entity.CronJob;
import com.mmo.cronjob.repository.CronJobRepository;
import com.mmo.cronjob.service.CronJobService;
import com.mmo.initialize.DataInitializer;
import com.mmo.module.fb.job.InitialFBDataJob;
import com.mmo.module.fb.job.LeagueSyncJob;
import com.mmo.module.fb.job.MatchDailyRecapJob;
import com.mmo.module.fb.job.MatchInsightJob;
import com.mmo.module.fb.job.MatchProcessPredictionJob;
import com.mmo.module.fb.job.MatchResultJob;
import com.mmo.module.fb.job.MatchUpcomingJob;
import com.mmo.module.fb.job.MatchDashboardJob;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FBCronjobInitial implements DataInitializer {
    private static final String FB_JOB_GROUP = "FBJobGroup";

    private final CronJobRepository cronJobRepository;
    private final CronJobService cronJobService;

    @Override
    public void initialize() {
        // =========================================================================
        // NOTE: ALL CRON EXPRESSIONS ARE IN UTC TIMEZONE
        // =========================================================================

        // 1. MASTER DATA JOBS
        createJobIfNotExist(LeagueSyncJob.class.getSimpleName(), LeagueSyncJob.class, "0 0 2 1 * ?",
                "Sync Leagues and Seasons (UTC 02:00)");
        createJobIfNotExist("TeamSyncJob", TeamSyncJob.class, "0 0 3 1,15 * ?",
                "Sync Teams by Leagues (UTC 03:00)");
        createJobIfNotExist("InitialFBDataJob", InitialFBDataJob.class, "0 0 0 1 8 ?",
                "Initial All Data FB Job");

        // 2. DAILY DATA PIPELINE
        // B1: Cào kết quả đêm qua (04:00 UTC = 11:00 AM VN)
        createJobIfNotExist("MatchResultSyncJob", MatchResultJob.class, "0 0 4 * * ?",
                "Fetch Match Results & Stats");

        // B2: Đánh giá hiệu suất ngày cũ (05:00 UTC = 12:00 PM VN)
        createJobIfNotExist("MatchDailyRecapJob", MatchDailyRecapJob.class, "0 0 5 * * ?",
                "Process Daily Recap & Accuracy");

        // B3: Cào danh sách trận hôm nay (05:30 UTC = 12:30 PM VN)
        createJobIfNotExist("MatchFixtureSyncJob", MatchUpcomingJob.class, "0 30 5 * * ?",
                "Fetch Today Fixtures");

        // B4: Cào Insight chuyên sâu (06:00 - 09:00 UTC = 13:00 - 16:00 PM VN)
        createJobIfNotExist("MatchInsightFetchJob", MatchInsightJob.class, "0 0/30 6-9 * * ?",
                "Fetch Deep Match Insights");

        // B5: Health Check Crawlers (09:30 UTC = 16:30 PM VN)
        createJobIfNotExist("CrawlerHealthCheckJob", CrawlerHealthCheckJob.class, "0 30 9 * * ?",
                "Check Playwright Crawler Health");

        // B6: Tính toán dự đoán (10:00 UTC = 17:00 PM VN)
        createJobIfNotExist("MatchProcessPredictionJob", MatchProcessPredictionJob.class, "0 0 10 * * ?",
                "Calculate Match Predictions");

        // B7: Render Dashboard (10:30 UTC = 17:30 PM VN)
        createJobIfNotExist("MatchDashboardPublishJob", MatchDashboardJob.class, "0 30 10 * * ?",
                "Prepare Dashboard Content");

        // 3. REALTIME & SYSTEM MONITORING
        // Đẩy tin tự động từ Queue ra Social/Telegram (Mỗi 2 phút)
        createJobIfNotExist("NotificationPublisherJob", NotificationPublisherJob.class, "0 */2 * * * ?",
                "Publish Queue Notifications");

        // Kiểm tra trận hoãn (Mỗi 3 tiếng)
        createJobIfNotExist("MatchStatusTrackerJob", MatchStatusTrackerJob.class, "0 0 */3 * * ?",
                "Track Match Status Changes");

        // Cập nhật Lineups & Odds sát giờ bóng lăn (15:00 - 21:00 UTC = 22:00 - 04:00 AM VN)
        createJobIfNotExist("MatchLineupOddsUpdateJob", MatchLineupOddsJob.class, "0 0/15 15-21 * * ?",
                "Update Lineups & Odds");

        // Dọn dẹp tài nguyên OS/DB (Chủ Nhật 01:00 UTC)
        createJobIfNotExist("DataCleanupJob", DataCleanupJob.class, "0 0 1 ? * SUN",
                "Cleanup Logs and Temp Files");
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
        }
    }

//    @Override
//    public void initialize() {
//        if (cronJobRepository.findByJobNameAndJobGroup(MatchResultJob.class.getSimpleName(), FB_JOB_GROUP).isEmpty()) {
//            final CronJob resultJob = new CronJob();
//            resultJob.setJobName(MatchResultJob.class.getSimpleName());
//            resultJob.setJobGroup(FB_JOB_GROUP);
//            resultJob.setJobClass(MatchResultJob.class.getName());
//            resultJob.setCronExpression("0 30 9 * * ?");
//            resultJob.setDescription("Fetch Match Results Job");
//            cronJobService.saveJob(resultJob);
//        }
//
//        if (cronJobRepository.findByJobNameAndJobGroup(MatchDailyRecapJob.class.getSimpleName(), FB_JOB_GROUP).isEmpty()) {
//            final CronJob dailyRecapJob = new CronJob();
//            dailyRecapJob.setJobName(MatchDailyRecapJob.class.getSimpleName());
//            dailyRecapJob.setJobGroup(FB_JOB_GROUP);
//            dailyRecapJob.setJobClass(MatchDailyRecapJob.class.getName());
//            dailyRecapJob.setCronExpression("0 0 10 * * ?");
//            dailyRecapJob.setDescription("Fetch Match Daily Recap Job");
//            cronJobService.saveJob(dailyRecapJob);
//        }
//
//        if (cronJobRepository.findByJobNameAndJobGroup(MatchUpcomingJob.class.getSimpleName(), FB_JOB_GROUP).isEmpty()) {
//            final CronJob matchUpcomingJob = new CronJob();
//            matchUpcomingJob.setJobName(MatchUpcomingJob.class.getSimpleName());
//            matchUpcomingJob.setJobGroup(FB_JOB_GROUP);
//            matchUpcomingJob.setJobClass(MatchUpcomingJob.class.getName());
//            matchUpcomingJob.setCronExpression("0 0 11 * * ?");
//            matchUpcomingJob.setDescription("Fetch Match Upcoming Job");
//            cronJobService.saveJob(matchUpcomingJob);
//        }
//
//        if (cronJobRepository.findByJobNameAndJobGroup(MatchProcessPredictionJob.class.getSimpleName(), FB_JOB_GROUP).isEmpty()) {
//            final CronJob processPredictionJob = new CronJob();
//            processPredictionJob.setJobName(MatchProcessPredictionJob.class.getSimpleName());
//            processPredictionJob.setJobGroup(FB_JOB_GROUP);
//            processPredictionJob.setJobClass(MatchProcessPredictionJob.class.getName());
//            processPredictionJob.setCronExpression("0 0 12 * * ?");
//            processPredictionJob.setDescription("Process calculate prediction Job");
//            cronJobService.saveJob(processPredictionJob);
//        }
//
//        if (cronJobRepository.findByJobNameAndJobGroup(MatchDashboardJob.class.getSimpleName(), FB_JOB_GROUP).isEmpty()) {
//            final CronJob matchDashboardJob = new CronJob();
//            matchDashboardJob.setJobName(MatchDashboardJob.class.getSimpleName());
//            matchDashboardJob.setJobGroup(FB_JOB_GROUP);
//            matchDashboardJob.setJobClass(MatchDashboardJob.class.getName());
//            matchDashboardJob.setCronExpression("0 0 13 * * ?");
//            matchDashboardJob.setDescription("Fetch Match Daily Upcoming Dashboard Job");
//            cronJobService.saveJob(matchDashboardJob);
//        }
//
//        if (cronJobRepository.findByJobNameAndJobGroup(MatchInsightJob.class.getSimpleName(), FB_JOB_GROUP).isEmpty()) {
//            final CronJob matchInsightJob = new CronJob();
//            matchInsightJob.setJobName(MatchInsightJob.class.getSimpleName());
//            matchInsightJob.setJobGroup(FB_JOB_GROUP);
//            matchInsightJob.setJobClass(MatchInsightJob.class.getName());
//            matchInsightJob.setCronExpression("0 */28 * * * ?");
//            matchInsightJob.setDescription("Fetch Match Upcoming Insights Job");
//            cronJobService.saveJob(matchInsightJob);
//        }
//
//        if (cronJobRepository.findByJobNameAndJobGroup(InitialFBDataJob.class.getSimpleName(), FB_JOB_GROUP).isEmpty()) {
//            final CronJob initialDataJob = new CronJob();
//            initialDataJob.setJobName(InitialFBDataJob.class.getSimpleName());
//            initialDataJob.setJobGroup(FB_JOB_GROUP);
//            initialDataJob.setJobClass(InitialFBDataJob.class.getName());
//            initialDataJob.setCronExpression("0 0 0 1 8 ?");
//            initialDataJob.setDescription("Initial All Data FB Job");
//            cronJobService.saveJob(initialDataJob);
//        }
//    }
}
