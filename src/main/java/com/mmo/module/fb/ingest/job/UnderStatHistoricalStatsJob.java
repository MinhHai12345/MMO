package com.mmo.module.fb.ingest.job;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.job.AbstractJob;
import com.mmo.module.fb.ingest.service.impl.FootballDataUkSyncService;
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
    private final FootballDataUkSyncService footballDataUkSyncService;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        log.info("Starting historical statistics provider sync");
        RuntimeException failure = null;

        try {
            understatSyncService.syncActiveSeasons();
        } catch (RuntimeException ex) {
            failure = ex;
            log.error("Understat historical statistics sync failed", ex);
        }

        try {
            footballDataUkSyncService.syncAllData();
        } catch (RuntimeException ex) {
            if (failure == null) {
                failure = ex;
            } else {
                failure.addSuppressed(ex);
            }
            log.error("Football-Data.co.uk historical sync failed", ex);
        }

        if (failure != null) {
            throw new IllegalStateException("One or more historical statistics providers failed", failure);
        }
        log.info("Historical statistics provider sync completed");
    }
}
