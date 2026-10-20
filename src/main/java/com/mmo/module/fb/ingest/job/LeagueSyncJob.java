package com.mmo.module.fb.ingest.job;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.job.AbstractJob;
import com.mmo.module.fb.ingest.service.LeagueIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class LeagueSyncJob extends AbstractJob<CronJob> {

    private final LeagueIngestionService leagueIngestionService;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        log.info("Starting league and season synchronization");
        leagueIngestionService.syncLeagues();
        log.info("League and season synchronization completed");
    }
}
