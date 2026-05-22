package com.mmo.module.fb.job.initialize;

import com.mmo.cronjob.entity.CronJob;
import com.mmo.cronjob.repository.CronJobRepository;
import com.mmo.cronjob.service.CronJobService;
import com.mmo.initialize.DataInitializer;
import com.mmo.module.fb.job.CleanUpStorageJob;
import com.mmo.module.fb.job.TextStorageJob;
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

        if (cronJobRepository.findByJobNameAndJobGroup("CleanUpStorageJob", FB_JOB_GROUP).isEmpty()) {
            final CronJob cleanUpCronJob = new CronJob();
            cleanUpCronJob.setJobName("CleanUpStorageJob");
            cleanUpCronJob.setJobGroup(FB_JOB_GROUP);
            cleanUpCronJob.setJobClass(CleanUpStorageJob.class.getName());
            cleanUpCronJob.setCronExpression("0 */2 * * * ?");
            cleanUpCronJob.setDescription("Job thực hiện gửi thông báo email tự động");

            cronJobService.saveJob(cleanUpCronJob);
        }

        if (cronJobRepository.findByJobNameAndJobGroup("TextStorageJob", FB_JOB_GROUP).isEmpty()) {
            final CronJob cleanUpCronJob = new CronJob();
            cleanUpCronJob.setJobName("TextStorageJob");
            cleanUpCronJob.setJobGroup(FB_JOB_GROUP);
            cleanUpCronJob.setJobClass(TextStorageJob.class.getName());
            cleanUpCronJob.setCronExpression("0 1/2 * * * ?");
            cleanUpCronJob.setDescription("Job thực hiện gửi thông báo email tự động");

            cronJobService.saveJob(cleanUpCronJob);
        }
    }
}
