package com.mmo.module.fb.ingest.job;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.job.AbstractJob;
import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.entity.MatchPrediction;
import com.mmo.module.fb.domain.repository.MatchPredictionRepository;
import com.mmo.module.fb.domain.repository.MatchRepository;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class ContentSchedulingJob extends AbstractJob<CronJob> {

    @Resource
    private MatchRepository matchRepository;

    @Resource
    private MatchPredictionRepository matchPredictionRepository;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        log.info("📅 Starting content scheduling...");

        Instant startTimeWindow = Instant.now().plus(6, ChronoUnit.HOURS);
        Instant endTimeWindow = startTimeWindow.plus(48, ChronoUnit.HOURS);

        List<Match> upcomingMatches = matchRepository.findByMatchTimeBetweenAndStatus(
                startTimeWindow, endTimeWindow, "SCHEDULED");

        if (CollectionUtils.isEmpty(upcomingMatches)) {
            log.info("✅ No upcoming matches for content scheduling");
            return;
        }

        int scheduledCount = 0;
        for (Match match : upcomingMatches) {
            MatchPrediction prediction = matchPredictionRepository.findByMatchId(match.getId()).orElse(null);
            
            if (prediction != null && "PENDING".equals(prediction.getPredictionStatus())) {
                // TODO: Create ContentQueue entries for different platforms
                // - Telegram: 1 hour before match
                // - Twitter: 2 hours before match
                // - Facebook: 3 hours before match
                scheduledCount++;
            }
        }

        log.info("✅ Content scheduling completed for {} matches", scheduledCount);
    }
}
