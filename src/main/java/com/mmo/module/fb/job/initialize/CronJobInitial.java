package com.mmo.module.fb.job.initialize;

import com.mmo.cronjob.entity.CronJob;
import com.mmo.cronjob.repository.CronJobRepository;
import com.mmo.cronjob.service.CronJobService;
import com.mmo.initialize.DataInitializer;
import com.mmo.module.fb.job.MatchInsightJob;
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
        if (cronJobRepository.findByJobNameAndJobGroup(MatchUpcomingJob.class.getSimpleName(), FB_JOB_GROUP).isEmpty()) {
            final CronJob matchUpcomingJob = new CronJob();
            matchUpcomingJob.setJobName(MatchUpcomingJob.class.getSimpleName());
            matchUpcomingJob.setJobGroup(FB_JOB_GROUP);
            matchUpcomingJob.setJobClass(MatchUpcomingJob.class.getName());
            matchUpcomingJob.setCronExpression("0 0 5 * * ?");
            matchUpcomingJob.setDescription("Fetch Match Upcoming Job");
            cronJobService.saveJob(matchUpcomingJob);
        }

        if (cronJobRepository.findByJobNameAndJobGroup(MatchDashboardJob.class.getSimpleName(), FB_JOB_GROUP).isEmpty()) {
            final CronJob matchDashboardJob = new CronJob();
            matchDashboardJob.setJobName(MatchDashboardJob.class.getSimpleName());
            matchDashboardJob.setJobGroup(FB_JOB_GROUP);
            matchDashboardJob.setJobClass(MatchDashboardJob.class.getName());
            matchDashboardJob.setCronExpression("0 0 7 * * ?");
            matchDashboardJob.setDescription("Fetch Match Upcoming Dashboard Job");
            cronJobService.saveJob(matchDashboardJob);
        }

        if (cronJobRepository.findByJobNameAndJobGroup(MatchInsightJob.class.getSimpleName(), FB_JOB_GROUP).isEmpty()) {
            final CronJob matchInsightJob = new CronJob();
            matchInsightJob.setJobName(MatchInsightJob.class.getSimpleName());
            matchInsightJob.setJobGroup(FB_JOB_GROUP);
            matchInsightJob.setJobClass(MatchInsightJob.class.getName());
            matchInsightJob.setCronExpression("0 */30 * * * ?");
            matchInsightJob.setDescription("Fetch Match Upcoming Insights Job");
            cronJobService.saveJob(matchInsightJob);
        }
    }
}
