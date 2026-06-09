package com.mmo.module.fb.job;

import com.mmo.cronjob.entity.CronJob;
import com.mmo.cronjob.job.AbstractJob;
import com.mmo.module.fb.entity.MatchPrediction;
import com.mmo.module.fb.entity.enums.MatchPredictionStatus;
import com.mmo.module.fb.predict.service.PredictionEngineService;
import com.mmo.module.fb.repository.MatchPredictionRepository;
import com.mmo.utils.DateTimeUtils;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Component
public class MatchProcessPredictionJob extends AbstractJob<CronJob> {
    @Resource
    private PredictionEngineService predictionEngineService;

    @Resource
    private MatchPredictionRepository matchPredictionRepository;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        LocalDateTime startTimeWindow = LocalDateTime.of(DateTimeUtils.todayLocalDate(), LocalTime.of(0, 0));
        LocalDateTime endTimeWindow = startTimeWindow.plusHours(48);
        List<MatchPrediction> predictions = matchPredictionRepository.findByStatusAndKickoffTimeBetweenOrderByKickoffTimeAsc(
                MatchPredictionStatus.PENDING, startTimeWindow, endTimeWindow);
        if (CollectionUtils.isNotEmpty(predictions)) {
            predictions.forEach(prediction -> {
                predictionEngineService.calculateMatchPredict(prediction);
                matchPredictionRepository.save(prediction);
            });
        }
    }

}
