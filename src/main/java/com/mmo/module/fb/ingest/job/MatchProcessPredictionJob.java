package com.mmo.module.fb.ingest.job;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.job.AbstractJob;
import com.mmo.module.fb.analytics.engine.CompositePredictionEngine;
import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.entity.MatchPrediction;
import com.mmo.module.fb.domain.enums.MatchStatus;
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
public class MatchProcessPredictionJob extends AbstractJob<CronJob> {

    @Resource
    private MatchPredictionRepository matchPredictionRepository;

    @Resource
    private MatchRepository matchRepository;

    @Resource
    private CompositePredictionEngine compositePredictionEngine;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        log.info("🔮 Starting match prediction processing...");

        Instant startTimeWindow = Instant.now().plus(6, ChronoUnit.HOURS);
        Instant endTimeWindow = startTimeWindow.plus(48, ChronoUnit.HOURS);

        List<Match> upcomingMatches = matchRepository.findByMatchTimeBetweenAndStatus(
                startTimeWindow, endTimeWindow, MatchStatus.SCHEDULED);

        if (CollectionUtils.isEmpty(upcomingMatches)) {
            log.info("✅ No upcoming matches found for prediction");
            return;
        }

        log.info("📊 Found {} upcoming matches to predict", upcomingMatches.size());

        int processedCount = 0;
        for (Match match : upcomingMatches) {
            try {
                MatchPrediction existingPrediction = matchPredictionRepository.findByMatchId(match.getId()).orElse(null);

                if (existingPrediction == null
                        || "PENDING".equals(existingPrediction.getPredictionStatus())
                        || "INSUFFICIENT_DATA".equals(existingPrediction.getPredictionStatus())) {
                    MatchPrediction prediction = compositePredictionEngine.predict(match);
                    if (prediction == null) {
                        continue;
                    }
                    // MatchPrediction uses @MapsId, so the match ID is its primary key.
                    matchPredictionRepository.save(prediction);
                    processedCount++;
                }
            } catch (Exception e) {
                log.error("❌ Error processing prediction for match {}: {}", match.getId(), e.getMessage());
            }
        }

        log.info("✅ Completed prediction processing for {} matches", processedCount);
    }

}
