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
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Component
public class MatchInsightJob extends AbstractJob<CronJob> {
    @Resource
    private TelegramService telegramService;

    @Resource
    private MatchPredictionRepository predictionRepository;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime upperLimit = now.plusHours(3);
        List<MatchPredictionStatus> targetStatuses = Arrays.asList(MatchPredictionStatus.FREE_DETAIL, MatchPredictionStatus.VIP_ONLY);
        List<MatchPrediction> incomingMatches = predictionRepository.findByStatusInAndKickoffTimeBetween(targetStatuses, now, upperLimit);

        if (CollectionUtils.isNotEmpty(incomingMatches)) {
            Map<Integer, List<MatchPrediction>> groupedMatches = groupMatchesByHour(incomingMatches);
            telegramService.notifyMatchesInsights(groupedMatches);
            incomingMatches.forEach((matchPrediction) -> {
                matchPrediction.setStatus(MatchPredictionStatus.POSTED);
            });
            predictionRepository.saveAll(incomingMatches);
        }
    }

    public Map<Integer, List<MatchPrediction>> groupMatchesByHour(List<MatchPrediction> matches) {
        return matches.stream()
                .collect(Collectors.groupingBy(
                        mp -> mp.getKickoffTime().getHour(),
                        TreeMap::new,
                        Collectors.toList()
                ));
    }
}
