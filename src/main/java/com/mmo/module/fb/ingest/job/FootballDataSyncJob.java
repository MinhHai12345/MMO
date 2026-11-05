package com.mmo.module.fb.ingest.job;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.job.AbstractJob;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.application.port.in.FootballDataSyncService;
import com.mmo.module.fb.ingest.service.ProviderOverrides;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

/** Thin Quartz adapter; all ingestion orchestration lives in the application service. */
@Slf4j
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class FootballDataSyncJob extends AbstractJob<CronJob> {

    private final FootballDataSyncService footballDataSyncService;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        ProviderOverrides overrides = new ProviderOverrides(
                IngestionJobParameters.provider(context, "league"),
                IngestionJobParameters.provider(context, "season"),
                IngestionJobParameters.provider(context, "team"),
                IngestionJobParameters.provider(context, "fixture"),
                IngestionJobParameters.provider(context, "standings"));
        Provider oddsOverride = IngestionJobParameters.provider(context, "odds");

        log.info("Starting football data sync: overrides={}, oddsProviderOverride={}", overrides, oddsOverride);
        footballDataSyncService.sync(overrides, oddsOverride);
        log.info("Football data sync completed");
    }
}
