package com.mmo.module.fb.ingest.job;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.job.AbstractJob;
import com.mmo.module.fb.ingest.service.impl.UnderStatCatalogSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

/** Scheduled full Understat league/team/player catalogue synchronization. */
@Slf4j
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class UnderStatCatalogSyncJob extends AbstractJob<CronJob> {
    private final UnderStatCatalogSyncService syncService;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        log.info("Starting Understat league/team/player catalogue sync");
        syncService.syncCatalog();
        log.info("Understat league/team/player catalogue sync completed");
    }
}
