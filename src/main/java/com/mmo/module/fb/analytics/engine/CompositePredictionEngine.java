package com.mmo.module.fb.analytics.engine;

import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.entity.MatchPrediction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

@Slf4j
@Component
public class CompositePredictionEngine {

    private static final int PROBABILITY_SCALE = 2;
    private static final int XG_SCALE = 2;

    private final List<PredictionEngine> engines;

    public CompositePredictionEngine(List<PredictionEngine> engines) {
        // Keep the aggregate separate from PredictionEngine beans to avoid a Spring
        // self-injection cycle. The filter also protects manual/test configurations.
        this.engines = engines.stream()
                .filter(Objects::nonNull)
                .filter(engine -> !"COMPOSITE".equalsIgnoreCase(engine.getEngineName()))
                .toList();
    }

    public MatchPrediction predict(Match match) {
        Objects.requireNonNull(match, "match must not be null");
        log.info("Running composite prediction for match {}", match.getId());

        List<WeightedPrediction> predictions = engines.stream()
                .map(engine -> new WeightedPrediction(engine.predict(match), validWeight(engine.getWeight())))
                .filter(item -> item.prediction() != null && item.weight() > 0)
                .toList();

        if (predictions.isEmpty()) {
            log.warn("No usable prediction engines available for match {}", match.getId());
            return createDefaultPrediction(match);
        }

        double homeProbability = weightedAverage(predictions, MatchPrediction::getWinHomeProb);
        double drawProbability = weightedAverage(predictions, MatchPrediction::getDrawProb);
        double awayProbability = weightedAverage(predictions, MatchPrediction::getWinAwayProb);

        double probabilityTotal = homeProbability + drawProbability + awayProbability;
        if (probabilityTotal <= 0) {
            homeProbability = 1.0 / 3.0;
            drawProbability = 1.0 / 3.0;
            awayProbability = 1.0 / 3.0;
        } else {
            homeProbability /= probabilityTotal;
            drawProbability /= probabilityTotal;
            awayProbability /= probabilityTotal;
        }

        return MatchPrediction.builder()
                .match(match)
                .predictedHomeXg(weightedAverageDecimal(predictions, MatchPrediction::getPredictedHomeXg, XG_SCALE))
                .predictedAwayXg(weightedAverageDecimal(predictions, MatchPrediction::getPredictedAwayXg, XG_SCALE))
                .winHomeProb(BigDecimal.valueOf(homeProbability).setScale(PROBABILITY_SCALE, RoundingMode.HALF_UP))
                .drawProb(BigDecimal.valueOf(drawProbability).setScale(PROBABILITY_SCALE, RoundingMode.HALF_UP))
                .winAwayProb(BigDecimal.valueOf(awayProbability).setScale(PROBABILITY_SCALE, RoundingMode.HALF_UP))
                .predictedResult(determineResult(homeProbability, drawProbability, awayProbability))
                .predictionStatus("PENDING")
                .confidenceScore(BigDecimal.valueOf(Math.max(homeProbability,
                        Math.max(drawProbability, awayProbability))).setScale(PROBABILITY_SCALE, RoundingMode.HALF_UP))
                .calculatedAt(Instant.now())
                .build();
    }

    private double validWeight(double weight) {
        return Double.isFinite(weight) && weight > 0 ? weight : 0;
    }

    private double weightedAverage(List<WeightedPrediction> predictions,
                                   Function<MatchPrediction, BigDecimal> valueExtractor) {
        double weightedSum = 0;
        double totalWeight = 0;

        for (WeightedPrediction item : predictions) {
            BigDecimal value = valueExtractor.apply(item.prediction());
            if (value == null || !Double.isFinite(value.doubleValue())) {
                continue;
            }
            weightedSum += value.doubleValue() * item.weight();
            totalWeight += item.weight();
        }

        return totalWeight == 0 ? 0 : weightedSum / totalWeight;
    }

    private BigDecimal weightedAverageDecimal(List<WeightedPrediction> predictions,
                                              Function<MatchPrediction, BigDecimal> valueExtractor,
                                              int scale) {
        double weightedSum = 0;
        double totalWeight = 0;

        for (WeightedPrediction item : predictions) {
            BigDecimal value = valueExtractor.apply(item.prediction());
            if (value == null || !Double.isFinite(value.doubleValue())) {
                continue;
            }
            weightedSum += value.doubleValue() * item.weight();
            totalWeight += item.weight();
        }

        if (totalWeight == 0) {
            return null;
        }
        return BigDecimal.valueOf(weightedSum / totalWeight).setScale(scale, RoundingMode.HALF_UP);
    }

    private String determineResult(double homeProb, double drawProb, double awayProb) {
        if (homeProb > drawProb && homeProb > awayProb) {
            return "HOME";
        }
        if (awayProb > drawProb && awayProb > homeProb) {
            return "AWAY";
        }
        return "DRAW";
    }

    private MatchPrediction createDefaultPrediction(Match match) {
        return MatchPrediction.builder()
                .match(match)
                .predictionStatus("PENDING")
                .winHomeProb(BigDecimal.valueOf(0.33))
                .drawProb(BigDecimal.valueOf(0.34))
                .winAwayProb(BigDecimal.valueOf(0.33))
                .predictedResult("DRAW")
                .confidenceScore(BigDecimal.valueOf(0.34))
                .calculatedAt(Instant.now())
                .build();
    }

    private record WeightedPrediction(MatchPrediction prediction, double weight) {
    }
}
