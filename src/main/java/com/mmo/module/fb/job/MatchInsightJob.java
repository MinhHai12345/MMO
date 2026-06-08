package com.mmo.module.fb.job;

import com.mmo.cronjob.entity.CronJob;
import com.mmo.cronjob.job.AbstractJob;
import com.mmo.module.fb.channel.service.TelegramService;
import com.mmo.module.fb.entity.MatchPrediction;
import com.mmo.module.fb.entity.enums.MatchPredictionStatus;
import com.mmo.module.fb.repository.MatchPredictionRepository;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class MatchInsightJob extends AbstractJob<CronJob> {
    @Resource
    private TelegramService telegramService;

    @Resource
    private MatchPredictionRepository predictionRepository;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        LocalDateTime now = LocalDateTime.now().plusDays(4);
        LocalDateTime upperLimit = now.plusHours(24);
        List<MatchPredictionStatus> targetStatuses = Arrays.asList(MatchPredictionStatus.FREE_DETAIL, MatchPredictionStatus.VIP_ONLY);
        List<MatchPrediction> incomingMatches = predictionRepository.findByStatusInAndKickoffTimeBetweenOrderByKickoffTimeAsc(targetStatuses, now, upperLimit);

        if (CollectionUtils.isNotEmpty(incomingMatches)) {
            populateIndex(incomingMatches);
            telegramService.notifyMatchesInsights(incomingMatches);
            incomingMatches.forEach((matchPrediction) -> matchPrediction.setStatus(MatchPredictionStatus.POSTED));
            predictionRepository.saveAll(incomingMatches);
        }
    }

    public void populateIndex(List<MatchPrediction> matches) {
        AtomicInteger globalIndex = new AtomicInteger(1);
        matches.forEach(it -> it.setIndex(globalIndex.getAndIncrement()));
    }
}
