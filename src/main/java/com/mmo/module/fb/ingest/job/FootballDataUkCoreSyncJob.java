package com.mmo.module.fb.ingest.job;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.job.AbstractJob;
import com.mmo.module.fb.ingest.service.impl.FootballDataUkSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

/**
 * Scheduled import of Football-Data.co.uk core match data.
 *
 * <p>The importer resolves provider league mappings and synchronizes all available seasons,
 * teams, fixtures, results and the team statistics available in the CSV files.
 * Football-Data.co.uk does not expose an official standings table.</p>
 */
@Slf4j
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class FootballDataUkCoreSyncJob extends AbstractJob<CronJob> {

    private final FootballDataUkSyncService footballDataUkSyncService;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        log.info("Starting Football-Data UK core-data synchronization");
        footballDataUkSyncService.syncAllData();
        log.info("Football-Data UK core-data synchronization completed");
    }
}
