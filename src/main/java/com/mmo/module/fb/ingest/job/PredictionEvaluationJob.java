package com.mmo.module.fb.ingest.job;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.job.AbstractJob;
import com.mmo.module.fb.analytics.service.PredictionEvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class PredictionEvaluationJob extends AbstractJob<CronJob> {

    private final PredictionEvaluationService predictionEvaluationService;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        log.info("Starting prediction evaluation for completed matches");
        int evaluated = predictionEvaluationService.evaluateFinishedPredictions();
        log.info("Prediction evaluation completed; evaluated={}", evaluated);
    }
}
