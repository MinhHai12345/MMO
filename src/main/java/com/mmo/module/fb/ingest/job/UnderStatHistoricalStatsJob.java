package com.mmo.module.fb.ingest.job;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.job.AbstractJob;
import com.mmo.module.fb.ingest.service.impl.UnderStatSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

/** Scheduled orchestration for historical match statistics providers. */
@Slf4j
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class UnderStatHistoricalStatsJob extends AbstractJob<CronJob> {

    private final UnderStatSyncService understatSyncService;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        // Kept as a compatibility entry point for manual rollback operations.
        // Football-Data.co.uk is deliberately not invoked here; it has one owner:
        // FootballDataSyncJob.
        log.info("Starting Understat-only historical statistics sync");
        understatSyncService.syncActiveSeasons();
        log.info("Understat-only historical statistics sync completed");
    }
}
