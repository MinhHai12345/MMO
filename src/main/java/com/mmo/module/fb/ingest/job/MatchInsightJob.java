package com.mmo.module.fb.ingest.job;

import com.mmo.core.converter.DynamicConverter;
import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.job.AbstractJob;
import com.mmo.module.fb.domain.repository.MatchPredictionRepository;
import com.mmo.module.fb.publish.model.PredictionData;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class MatchInsightJob extends AbstractJob<CronJob> {
//    @Resource
//    private TelegramService telegramService;

    @Resource
    private DynamicConverter dynamicConverter;

    @Resource
    private MatchPredictionRepository predictionRepository;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
//        LocalDateTime now = LocalDateTime.now();
//        LocalDateTime upperLimit = now.plusHours(24);
//        List<MatchPredictionStatus> targetStatuses = List.of(MatchPredictionStatus.READY);
//        List<MatchPrediction> incomingMatches = predictionRepository.findByStatusInAndKickoffTimeBetweenOrderByKickoffTimeAsc(targetStatuses, now, upperLimit);
//
//        if (CollectionUtils.isNotEmpty(incomingMatches)) {
//            List<PredictionData> predictions = dynamicConverter.convertAll(incomingMatches, PredictionData.class);
//            populateIndex(predictions);
//            telegramService.notifyMatchesInsights(predictions);
//            incomingMatches.forEach((matchPrediction) -> matchPrediction.setStatus(MatchPredictionStatus.POSTED));
//            predictionRepository.saveAll(incomingMatches);
//        }
    }

    public void populateIndex(List<PredictionData> matches) {
        AtomicInteger globalIndex = new AtomicInteger(1);
        matches.forEach(it -> it.setIndex(globalIndex.getAndIncrement()));
    }
}
