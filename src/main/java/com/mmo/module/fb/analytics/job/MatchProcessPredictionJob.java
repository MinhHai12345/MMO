package com.mmo.module.fb.analytics.job;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.job.AbstractJob;
import com.mmo.module.fb.analytics.service.MatchPredictionProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

/** Thin Quartz adapter for the application-level upcoming-prediction use case. */
@Slf4j
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class MatchProcessPredictionJob extends AbstractJob<CronJob> {

    private final MatchPredictionProcessingService predictionProcessingService;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        var report = predictionProcessingService.processUpcomingMatches(this::shouldStop);
        log.info("Prediction Quartz job completed: status={}, candidates={}, processed={}, skipped={}, failed={}",
                report.status(), report.candidates(), report.processed(),
                report.skipped(), report.failed());
    }
}
