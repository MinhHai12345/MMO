package com.mmo.module.fb.ingest.job;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.job.AbstractJob;
import com.mmo.module.fb.repository.MatchPredictionRepository;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class MatchProcessPredictionJob extends AbstractJob<CronJob> {

    @Resource
    private MatchPredictionRepository matchPredictionRepository;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
//        LocalDateTime startTimeWindow = LocalDateTime.of(DateTimeUtils.todayLocalDate(), LocalTime.of(13, 0));
//        LocalDateTime endTimeWindow = startTimeWindow.plusHours(24);
//        List<MatchPrediction> predictions = matchPredictionRepository.findByStatusAndKickoffTimeBetweenOrderByKickoffTimeAsc(
//                MatchPredictionStatus.PENDING, startTimeWindow, endTimeWindow);
//        if (CollectionUtils.isNotEmpty(predictions)) {
//            predictions.forEach(prediction -> {
//                predictionEngineService.calculateMatchPredict(prediction);
//                matchPredictionRepository.save(prediction);
//            });
//        }
    }

}
