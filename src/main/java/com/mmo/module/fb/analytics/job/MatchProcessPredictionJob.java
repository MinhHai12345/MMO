package com.mmo.module.fb.analytics.job;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.job.AbstractJob;
import com.mmo.module.fb.analytics.engine.CompositePredictionEngine;
import com.mmo.module.fb.analytics.entity.MatchPrediction;
import com.mmo.module.fb.analytics.repository.MatchPredictionRepository;
import com.mmo.module.fb.match.entity.Match;
import com.mmo.module.fb.match.enums.MatchStatus;
import com.mmo.module.fb.match.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class MatchProcessPredictionJob extends AbstractJob<CronJob> {

    private static final int BATCH_SIZE = 100;
    private static final long MIN_HOURS_BEFORE_KICKOFF = 6;
    private static final long MAX_HOURS_BEFORE_KICKOFF = 54;
    private static final String PENDING = "PENDING";
    private static final String INSUFFICIENT_DATA = "INSUFFICIENT_DATA";

    private final MatchPredictionRepository matchPredictionRepository;
    private final MatchRepository matchRepository;
    private final CompositePredictionEngine compositePredictionEngine;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        Instant now = Instant.now();
        Instant from = now.plus(MIN_HOURS_BEFORE_KICKOFF, ChronoUnit.HOURS);
        Instant to = now.plus(MAX_HOURS_BEFORE_KICKOFF, ChronoUnit.HOURS);

        int candidates = 0;
        int processed = 0;
        int skipped = 0;
        int failed = 0;
        int page = 0;

        log.info("Prediction run started: window=[{}, {}), batchSize={}", from, to, BATCH_SIZE);
        while (!shouldStop()) {
            List<Match> matches = matchRepository.findPredictionCandidates(
                    from, to, MatchStatus.SCHEDULED, PageRequest.of(page, BATCH_SIZE));
            if (matches.isEmpty()) {
                break;
            }

            candidates += matches.size();
            List<Long> matchIds = matches.stream().map(Match::getId).toList();
            Map<Long, MatchPrediction> existingByMatchId = new HashMap<>();
            matchPredictionRepository.findAllById(matchIds)
                    .forEach(prediction -> existingByMatchId.put(prediction.getId(), prediction));

            for (Match match : matches) {
                if (shouldStop()) {
                    break;
                }

                try {
                    MatchPrediction existing = existingByMatchId.get(match.getId());
                    if (existing != null
                            && !PENDING.equals(existing.getPredictionStatus())
                            && !INSUFFICIENT_DATA.equals(existing.getPredictionStatus())) {
                        skipped++;
                        continue;
                    }

                    MatchPrediction prediction = compositePredictionEngine.predict(match);
                    if (prediction == null) {
                        skipped++;
                        continue;
                    }

                    matchPredictionRepository.save(prediction);
                    processed++;
                } catch (RuntimeException exception) {
                    failed++;
                    log.error("Prediction failed for matchId={}", match.getId(), exception);
                }
            }

            if (matches.size() < BATCH_SIZE) {
                break;
            }
            page++;
        }

        log.info("Prediction run finished: candidates={}, processed={}, skipped={}, failed={}, stopped={}",
                candidates, processed, skipped, failed, shouldStop());

        if (failed > 0) {
            log.warn("Prediction run completed with {} match-level failure(s); inspect preceding errors", failed);
        }
    }
}
