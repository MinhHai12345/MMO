package com.mmo.module.fb.job;

import com.mmo.cronjob.entity.CronJob;
import com.mmo.cronjob.job.AbstractJob;
import com.mmo.module.fb.channel.service.TelegramService;
import com.mmo.module.fb.entity.MatchPrediction;
import com.mmo.module.fb.entity.enums.MatchPredictionStatus;
import com.mmo.module.fb.repository.MatchPredictionRepository;
import com.mmo.utils.DateTimeUtils;
import jakarta.annotation.Resource;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

@Component
public class MatchDashboardJob extends AbstractJob<CronJob> {
    @Resource
    private TelegramService telegramService;

    @Resource
    private MatchPredictionRepository predictionRepository;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        LocalDateTime startTimeWindow = LocalDateTime.of(DateTimeUtils.todayLocalDate(), LocalTime.of(13, 0));
        LocalDateTime endTimeWindow = startTimeWindow.plusHours(24);

        List<MatchPrediction> readyPredictions = predictionRepository
                .findByStatusAndKickoffTimeBetweenOrderByKickoffTimeAsc(MatchPredictionStatus.READY, startTimeWindow, endTimeWindow);
        if (CollectionUtils.isNotEmpty(readyPredictions)) {
//            MatchClassification matchClassification = classifyMatches(readyPredictions);
            telegramService.notifyMatchesDashboard(Collections.emptyList(), readyPredictions);
            predictionRepository.saveAll(readyPredictions);
        }
    }

//    private MatchClassification classifyMatches(List<MatchPrediction> valueMatches) {
//        List<MatchPrediction> freeMatches = new ArrayList<>();
//        List<MatchPrediction> vipMatches = new ArrayList<>();
//
//        for (MatchPrediction mp : valueMatches) {
//            if (mp.isPremium()) {
//                mp.setStatus(MatchPredictionStatus.VIP_ONLY);
//                vipMatches.add(mp);
//            } else {
//                if (freeMatches.size() < 3) {
//                    mp.setStatus(MatchPredictionStatus.FREE_DETAIL);
//                    freeMatches.add(mp);
//                } else {
//                    mp.setStatus(MatchPredictionStatus.VIP_ONLY);
//                    vipMatches.add(mp);
//                }
//            }
//        }
//        return new MatchClassification(freeMatches, vipMatches);
//    }

    @Getter
    @RequiredArgsConstructor
    private static class MatchClassification {
        private final List<MatchPrediction> freeMatches;
        private final List<MatchPrediction> vipMatches;
    }

}

