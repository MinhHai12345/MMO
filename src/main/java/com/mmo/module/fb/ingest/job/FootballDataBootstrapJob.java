package com.mmo.module.fb.ingest.job;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.job.AbstractJob;
import com.mmo.module.fb.ingest.service.FootballDataBootstrapService;
import com.mmo.module.fb.ingest.service.ProviderOverrides;
import com.mmo.module.fb.ingest.enums.Provider;
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
        ProviderOverrides overrides = new ProviderOverrides(
                IngestionJobParameters.provider(context, "league"),
                IngestionJobParameters.provider(context, "team"),
                IngestionJobParameters.provider(context, "fixture"),
                IngestionJobParameters.provider(context, "standings"));

        log.info("Starting core-data bootstrap with provider overrides {}", overrides);
        footballDataBootstrapService.syncCoreData(overrides);
        log.info("Core-data bootstrap completed with provider overrides {}", overrides);
    }
}
