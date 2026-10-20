package com.mmo.module.fb.ingest.job;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.job.AbstractJob;
import com.mmo.module.fb.ingest.service.FootballDataBootstrapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class FootballDataBootstrapJob extends AbstractJob<CronJob> {

    private final FootballDataBootstrapService footballDataBootstrapService;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        log.info("Starting end-to-end API-Football core data sync");
        footballDataBootstrapService.syncCoreData();
        log.info("End-to-end API-Football core data sync completed");
    }
}
