package com.mmo.module.fb.job;

import com.mmo.cronjob.entity.CronJob;
import com.mmo.cronjob.job.AbstractJob;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

@Component
public class CleanUpStorageJob extends AbstractJob<CronJob> {

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        System.out.println("-----Clean up storage-----");
    }

}
