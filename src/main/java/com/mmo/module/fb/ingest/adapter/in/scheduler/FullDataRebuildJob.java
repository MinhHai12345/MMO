package com.mmo.module.fb.ingest.adapter.in.scheduler;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.job.AbstractJob;
import com.mmo.module.fb.ingest.application.port.in.FullDataRebuildUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

/** Quartz entry point for an operator-triggered full ingestion rebuild. */
@Slf4j
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class FullDataRebuildJob extends AbstractJob<CronJob> {

    private final FullDataRebuildUseCase fullDataRebuildUseCase;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        log.warn("Operator-triggered full data rebuild started");
        fullDataRebuildUseCase.rebuildAllData();
        log.warn("Operator-triggered full data rebuild completed");
    }
}
