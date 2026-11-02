package com.mmo.module.fb.ingest.job;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.job.AbstractJob;
import com.mmo.module.fb.ingest.service.impl.UnderStatSyncService;
import com.mmo.module.fb.ingest.service.impl.FootballDataUkSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

/** Scheduled enrichment job for Understat xG and team-level advanced statistics. */
@Slf4j
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class UnderStatHistoricalStatsJob extends AbstractJob<CronJob> {

    private final UnderStatSyncService understatSyncService;
    private final FootballDataUkSyncService footballDataUkSyncService;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        log.info("Starting historical statistics sync");
        understatSyncService.syncActiveSeasons();
        footballDataUkSyncService.syncActiveSeasons();
        log.info("Historical statistics sync completed");
    }
}
