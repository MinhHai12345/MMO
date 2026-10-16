package com.mmo.module.fb.analytics.service.impl;

import com.mmo.module.fb.analytics.service.PredictionEvaluationService;

import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.entity.MatchPrediction;
import com.mmo.module.fb.domain.entity.PredictionPerformance;
import com.mmo.module.fb.domain.enums.MatchStatus;
import com.mmo.module.fb.domain.repository.MatchPredictionRepository;
import com.mmo.module.fb.domain.repository.PredictionPerformanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PredictionEvaluationServiceImpl implements PredictionEvaluationService {

    private static final int BATCH_SIZE = 500;
    private static final int SCORE_SCALE = 2;

    private final MatchPredictionRepository matchPredictionRepository;
    private final PredictionPerformanceRepository predictionPerformanceRepository;

    @Transactional
    public int evaluateFinishedPredictions() {
        List<MatchPrediction> predictions = matchPredictionRepository.findUnevaluatedPredictions(
                MatchStatus.FINISHED, PageRequest.of(0, BATCH_SIZE));
        int evaluated = 0;

        for (MatchPrediction prediction : predictions) {
            Match match = prediction.getMatch();
            if (match == null || match.getHomeScore() == null || match.getAwayScore() == null) {
                continue;
            }

            String actualResult = determineActualResult(match.getHomeScore(), match.getAwayScore());
            Double homeProbability = probability(prediction.getWinHomeProb());
            Double drawProbability = probability(prediction.getDrawProb());
            Double awayProbability = probability(prediction.getWinAwayProb());

            if (homeProbability == null || drawProbability == null || awayProbability == null) {
                continue;
            }

            double probabilityTotal = homeProbability + drawProbability + awayProbability;
            if (!Double.isFinite(probabilityTotal) || probabilityTotal <= 0) {
                continue;
            }

            // Normalize historical predictions too, because older records may not sum to 1.
            homeProbability /= probabilityTotal;
            drawProbability /= probabilityTotal;
            awayProbability /= probabilityTotal;

            double homeError = homeProbability - ("HOME".equals(actualResult) ? 1.0 : 0.0);
            double drawError = drawProbability - ("DRAW".equals(actualResult) ? 1.0 : 0.0);
            double awayError = awayProbability - ("AWAY".equals(actualResult) ? 1.0 : 0.0);
            double brierScore = homeError * homeError + drawError * drawError + awayError * awayError;

            // Normalized multiclass Brier quality: 100 is perfect, 0 is worst possible.
            BigDecimal accuracyScore = BigDecimal.valueOf(100.0 * (1.0 - brierScore / 2.0))
                    .max(BigDecimal.ZERO)
                    .min(BigDecimal.valueOf(100))
                    .setScale(SCORE_SCALE, RoundingMode.HALF_UP);
            boolean correct = actualResult.equals(prediction.getPredictedResult());

            PredictionPerformance performance = PredictionPerformance.builder()
                    .prediction(prediction)
                    .actualHomeScore(match.getHomeScore())
                    .actualAwayScore(match.getAwayScore())
                    .actualResult(actualResult)
                    .isCorrect(correct)
                    .accuracyScore(accuracyScore)
                    .evaluatedAt(Instant.now())
                    .build();

            predictionPerformanceRepository.save(performance);
            prediction.setResult(actualResult);
            prediction.setPredictionStatus("EVALUATED");
            matchPredictionRepository.save(prediction);
            evaluated++;
        }

        log.info("Evaluated {} finished match predictions", evaluated);
        return evaluated;
    }

    private Double probability(BigDecimal value) {
        if (value == null) {
            return null;
        }
        double probability = value.doubleValue();
        return Double.isFinite(probability) && probability >= 0 && probability <= 1
                ? probability : null;
    }

    private String determineActualResult(int homeScore, int awayScore) {
        if (homeScore > awayScore) {
            return "HOME";
        }
        if (homeScore < awayScore) {
            return "AWAY";
        }
        return "DRAW";
    }
}
