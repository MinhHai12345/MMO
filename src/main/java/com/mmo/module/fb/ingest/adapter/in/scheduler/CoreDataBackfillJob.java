package com.mmo.module.fb.ingest.adapter.in.scheduler;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.job.AbstractJob;
import com.mmo.module.fb.ingest.application.port.in.CoreDataBackfillUseCase;
import com.mmo.module.fb.ingest.application.model.ProviderOverrides;
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
public class CoreDataBackfillJob extends AbstractJob<CronJob> {

    private final CoreDataBackfillUseCase coreDataBackfillUseCase;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        ProviderOverrides overrides = new ProviderOverrides(
                IngestionJobParameters.provider(context, "league"),
                IngestionJobParameters.provider(context, "season"),
                IngestionJobParameters.provider(context, "team"),
                IngestionJobParameters.provider(context, "fixture"),
                IngestionJobParameters.provider(context, "standings"));

        log.info("Starting core-data bootstrap with provider overrides {}", overrides);
        coreDataBackfillUseCase.syncCoreData(overrides);
        log.info("Core-data bootstrap completed with provider overrides {}", overrides);
    }
}
