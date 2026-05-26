package com.mmo.module.fb.job;

import com.mmo.cronjob.entity.CronJob;
import com.mmo.cronjob.job.AbstractJob;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

@Component
public class MatchInsightJob extends AbstractJob<CronJob> {

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        System.out.println("-----Fetch Match Upcoming Insights Job-----");

    }

}
