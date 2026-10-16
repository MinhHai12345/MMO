package com.mmo.module.fb.analytics.engine;

import com.mmo.module.fb.analytics.service.MatchHistoryAnalyticsService;
import com.mmo.module.fb.analytics.service.MatchHistoryAnalyticsService.ExpectedGoals;
import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.entity.MatchPrediction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class StatisticalPredictionEngine implements PredictionEngine {

    private static final int PROBABILITY_SCALE = 4;

    private final MatchHistoryAnalyticsService analyticsService;

    @Override
    public MatchPrediction predict(Match match) {
        Optional<ExpectedGoals> expectedGoals = analyticsService.estimateFromGoals(match);
        if (expectedGoals.isEmpty()) {
            log.debug("Insufficient completed-match history for statistical prediction, matchId={}",
                    match == null ? null : match.getId());
            return null;
        }

        ExpectedGoals estimate = expectedGoals.get();
        PoissonProbabilityCalculator.Result probabilities =
                PoissonProbabilityCalculator.calculate(estimate.home(), estimate.away());

        return MatchPrediction.builder()
                .match(match)
                // This engine is based on actual goals, not xG. Do not mislabel goal rates as xG.
                .predictedHomeXg(null)
                .predictedAwayXg(null)
                .winHomeProb(probability(probabilities.homeWin()))
                .drawProb(probability(probabilities.draw()))
                .winAwayProb(probability(probabilities.awayWin()))
                .predictedResult(determineResult(probabilities))
                .predictionStatus("PENDING")
                .confidenceScore(BigDecimal.valueOf(Math.max(probabilities.homeWin(),
                        Math.max(probabilities.draw(), probabilities.awayWin())))
                        .setScale(2, RoundingMode.HALF_UP))
                .calculatedAt(Instant.now())
                .predictionReasoning(String.format(
                        "Goal-history Poisson model; expected goals %.2f-%.2f; samples home=%d away=%d",
                        estimate.home(), estimate.away(), estimate.homeSamples(), estimate.awaySamples()))
                .build();
    }

    @Override
    public String getEngineName() {
        return "STATISTICAL";
    }

    @Override
    public double getWeight() {
        return 0.6;
    }

    private BigDecimal probability(double value) {
        return BigDecimal.valueOf(value).setScale(PROBABILITY_SCALE, RoundingMode.HALF_UP);
    }

    private String determineResult(PoissonProbabilityCalculator.Result probabilities) {
        if (probabilities.homeWin() > probabilities.draw()
                && probabilities.homeWin() > probabilities.awayWin()) {
            return "HOME";
        }
        if (probabilities.awayWin() > probabilities.draw()
                && probabilities.awayWin() > probabilities.homeWin()) {
            return "AWAY";
        }
        return "DRAW";
    }
}
