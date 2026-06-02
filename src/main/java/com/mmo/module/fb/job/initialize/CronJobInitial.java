package com.mmo.module.fb.job.initialize;

import com.mmo.cronjob.entity.CronJob;
import com.mmo.cronjob.repository.CronJobRepository;
import com.mmo.cronjob.service.CronJobService;
import com.mmo.initialize.DataInitializer;
import com.mmo.module.fb.job.InitialFBDataJob;
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
public class CronJobInitial implements DataInitializer {
    private static final String FB_JOB_GROUP = "FBJobGroup";

    private final CronJobRepository cronJobRepository;
    private final CronJobService cronJobService;

    @Override
    public void initialize() {
        if (cronJobRepository.findByJobNameAndJobGroup(MatchResultJob.class.getSimpleName(), FB_JOB_GROUP).isEmpty()) {
            final CronJob resultJob = new CronJob();
            resultJob.setJobName(MatchResultJob.class.getSimpleName());
            resultJob.setJobGroup(FB_JOB_GROUP);
            resultJob.setJobClass(MatchResultJob.class.getName());
            resultJob.setCronExpression("0 30 9 * * ?");
            resultJob.setDescription("Fetch Match Upcoming Job");
            cronJobService.saveJob(resultJob);
        }

        if (cronJobRepository.findByJobNameAndJobGroup(MatchDailyRecapJob.class.getSimpleName(), FB_JOB_GROUP).isEmpty()) {
            final CronJob dailyRecapJob = new CronJob();
            dailyRecapJob.setJobName(MatchDailyRecapJob.class.getSimpleName());
            dailyRecapJob.setJobGroup(FB_JOB_GROUP);
            dailyRecapJob.setJobClass(MatchDailyRecapJob.class.getName());
            dailyRecapJob.setCronExpression("0 0 10 * * ?");
            dailyRecapJob.setDescription("Fetch Match Upcoming Job");
            cronJobService.saveJob(dailyRecapJob);
        }

        if (cronJobRepository.findByJobNameAndJobGroup(MatchUpcomingJob.class.getSimpleName(), FB_JOB_GROUP).isEmpty()) {
            final CronJob matchUpcomingJob = new CronJob();
            matchUpcomingJob.setJobName(MatchUpcomingJob.class.getSimpleName());
            matchUpcomingJob.setJobGroup(FB_JOB_GROUP);
            matchUpcomingJob.setJobClass(MatchUpcomingJob.class.getName());
            matchUpcomingJob.setCronExpression("0 0 11 * * ?");
            matchUpcomingJob.setDescription("Fetch Match Upcoming Job");
            cronJobService.saveJob(matchUpcomingJob);
        }

        if (cronJobRepository.findByJobNameAndJobGroup(MatchProcessPredictionJob.class.getSimpleName(), FB_JOB_GROUP).isEmpty()) {
            final CronJob processPredictionJob = new CronJob();
            processPredictionJob.setJobName(MatchProcessPredictionJob.class.getSimpleName());
            processPredictionJob.setJobGroup(FB_JOB_GROUP);
            processPredictionJob.setJobClass(MatchProcessPredictionJob.class.getName());
            processPredictionJob.setCronExpression("0 0 12 * * ?");
            processPredictionJob.setDescription("Process calculate prediction Job");
            cronJobService.saveJob(processPredictionJob);
        }

        if (cronJobRepository.findByJobNameAndJobGroup(MatchDashboardJob.class.getSimpleName(), FB_JOB_GROUP).isEmpty()) {
            final CronJob matchDashboardJob = new CronJob();
            matchDashboardJob.setJobName(MatchDashboardJob.class.getSimpleName());
            matchDashboardJob.setJobGroup(FB_JOB_GROUP);
            matchDashboardJob.setJobClass(MatchDashboardJob.class.getName());
            matchDashboardJob.setCronExpression("0 0 13 * * ?");
            matchDashboardJob.setDescription("Fetch Match Upcoming Dashboard Job");
            cronJobService.saveJob(matchDashboardJob);
        }

        if (cronJobRepository.findByJobNameAndJobGroup(MatchInsightJob.class.getSimpleName(), FB_JOB_GROUP).isEmpty()) {
            final CronJob matchInsightJob = new CronJob();
            matchInsightJob.setJobName(MatchInsightJob.class.getSimpleName());
            matchInsightJob.setJobGroup(FB_JOB_GROUP);
            matchInsightJob.setJobClass(MatchInsightJob.class.getName());
            matchInsightJob.setCronExpression("0 */28 * * * ?");
            matchInsightJob.setDescription("Fetch Match Upcoming Insights Job");
            cronJobService.saveJob(matchInsightJob);
        }

        if (cronJobRepository.findByJobNameAndJobGroup(InitialFBDataJob.class.getSimpleName(), FB_JOB_GROUP).isEmpty()) {
            final CronJob initialDataJob = new CronJob();
            initialDataJob.setJobName(InitialFBDataJob.class.getSimpleName());
            initialDataJob.setJobGroup(FB_JOB_GROUP);
            initialDataJob.setJobClass(InitialFBDataJob.class.getName());
            initialDataJob.setCronExpression("0 0 0 1 8 ?");
            initialDataJob.setDescription("Initial All Data FB Job");
            cronJobService.saveJob(initialDataJob);
        }

    }
}
