package com.mmo.module.fb.analytics.job;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.job.AbstractJob;
import com.mmo.module.fb.analytics.service.AdvancedMetricsProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

/** Thin Quartz adapter for rebuilding derived football analytics metrics. */
@Slf4j
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class AdvancedMetricsJob extends AbstractJob<CronJob> {

    private final AdvancedMetricsProcessingService advancedMetricsProcessingService;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        var report = advancedMetricsProcessingService.process(this::shouldStop);
        log.info("Advanced metrics Quartz job completed: status={}, teamFormsUpdated={}, h2hRecordsUpdated={}",
                report.status(), report.teamFormsUpdated(), report.h2hRecordsUpdated());
    }
}
