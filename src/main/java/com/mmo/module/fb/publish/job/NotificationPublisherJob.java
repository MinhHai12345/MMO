package com.mmo.module.fb.publish.job;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.job.AbstractJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class NotificationPublisherJob extends AbstractJob<CronJob> {

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {

    }
}
