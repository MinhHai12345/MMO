package com.mmo.module.fb.analytics.engine;

import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.entity.MatchPrediction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
public class CompositePredictionEngine {

    private static final int PROBABILITY_SCALE = 2;
    private static final int XG_SCALE = 2;

    private final List<PredictionEngine> engines;

    public CompositePredictionEngine(List<PredictionEngine> engines) {
        this.engines = engines.stream()
                .filter(Objects::nonNull)
                .filter(engine -> !"COMPOSITE".equalsIgnoreCase(engine.getEngineName()))
                .toList();
    }

    public MatchPrediction predict(Match match) {
        Objects.requireNonNull(match, "match must not be null");

        List<PredictionSample> samples = new ArrayList<>();
        for (PredictionEngine engine : engines) {
            double weight = validWeight(engine.getWeight());
            if (weight == 0) {
                continue;
            }

            try {
                MatchPrediction prediction = engine.predict(match);
                PredictionSample sample = toSample(engine.getEngineName(), prediction, weight);
                if (sample != null) {
                    samples.add(sample);
                } else {
                    log.debug("Prediction engine {} had insufficient or invalid data for matchId={}",
                            engine.getEngineName(), match.getId());
                }
            } catch (RuntimeException exception) {
                // One unavailable model must not prevent other independent models from running.
                log.warn("Prediction engine {} failed for matchId={}: {}",
                        engine.getEngineName(), match.getId(), exception.getMessage());
            }
        }

        if (samples.isEmpty()) {
            log.info("No analytics model has enough valid data for matchId={}", match.getId());
            return insufficientDataPrediction(match);
        }

        double homeProbability = weightedAverage(samples, PredictionSample::homeProbability);
        double drawProbability = weightedAverage(samples, PredictionSample::drawProbability);
        double awayProbability = weightedAverage(samples, PredictionSample::awayProbability);
        double total = homeProbability + drawProbability + awayProbability;

        if (!Double.isFinite(total) || total <= 0) {
            return insufficientDataPrediction(match);
        }

        homeProbability /= total;
        drawProbability /= total;
        awayProbability /= total;

        BigDecimal roundedHome = probability(homeProbability);
        BigDecimal roundedDraw = probability(drawProbability);
        BigDecimal roundedAway = probability(awayProbability);
        normalizeRoundedProbabilities(roundedHome, roundedDraw, roundedAway,
                homeProbability, drawProbability, awayProbability);

        return MatchPrediction.builder()
                .match(match)
                .predictedHomeXg(weightedAverageDecimal(samples, PredictionSample::homeXg))
                .predictedAwayXg(weightedAverageDecimal(samples, PredictionSample::awayXg))
                .winHomeProb(roundedHome)
                .drawProb(roundedDraw)
                .winAwayProb(roundedAway)
                .predictedResult(determineResult(homeProbability, drawProbability, awayProbability))
                .predictionStatus("PENDING")
                .confidenceScore(BigDecimal.valueOf(Math.max(homeProbability,
                        Math.max(drawProbability, awayProbability))).setScale(2, RoundingMode.HALF_UP))
                .calculatedAt(Instant.now())
                .predictionReasoning("Ensemble models: " + String.join(", ",
                        samples.stream().map(PredictionSample::engineName).toList()))
                .build();
    }

    private PredictionSample toSample(String engineName, MatchPrediction prediction, double weight) {
        if (prediction == null || prediction.getWinHomeProb() == null
                || prediction.getDrawProb() == null || prediction.getWinAwayProb() == null) {
            return null;
        }

        double home = prediction.getWinHomeProb().doubleValue();
        double draw = prediction.getDrawProb().doubleValue();
        double away = prediction.getWinAwayProb().doubleValue();
        if (!validProbability(home) || !validProbability(draw) || !validProbability(away)) {
            return null;
        }

        double total = home + draw + away;
        if (!Double.isFinite(total) || total <= 0) {
            return null;
        }

        return new PredictionSample(engineName, prediction, weight,
                home / total, draw / total, away / total);
    }

    private boolean validProbability(double value) {
        return Double.isFinite(value) && value >= 0 && value <= 1;
    }

    private double validWeight(double weight) {
        return Double.isFinite(weight) && weight > 0 ? weight : 0;
    }

    private double weightedAverage(List<PredictionSample> samples,
                                   java.util.function.ToDoubleFunction<PredictionSample> extractor) {
        double weightedSum = 0;
        double totalWeight = 0;
        for (PredictionSample sample : samples) {
            double value = extractor.applyAsDouble(sample);
            weightedSum += value * sample.weight();
            totalWeight += sample.weight();
        }
        return totalWeight == 0 ? 0 : weightedSum / totalWeight;
    }

    private BigDecimal weightedAverageDecimal(List<PredictionSample> samples,
                                              java.util.function.Function<MatchPrediction, BigDecimal> extractor) {
        double weightedSum = 0;
        double totalWeight = 0;
        for (PredictionSample sample : samples) {
            BigDecimal value = extractor.apply(sample.prediction());
            if (value == null || !Double.isFinite(value.doubleValue()) || value.signum() < 0) {
                continue;
            }
            weightedSum += value.doubleValue() * sample.weight();
            totalWeight += sample.weight();
        }

        return totalWeight == 0
                ? null
                : BigDecimal.valueOf(weightedSum / totalWeight).setScale(XG_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal probability(double value) {
        return BigDecimal.valueOf(value).setScale(PROBABILITY_SCALE, RoundingMode.HALF_UP);
    }

    private void normalizeRoundedProbabilities(BigDecimal home, BigDecimal draw, BigDecimal away,
                                               double rawHome, double rawDraw, double rawAway) {
        BigDecimal difference = BigDecimal.ONE.setScale(PROBABILITY_SCALE)
                .subtract(home.add(draw).add(away));

        if (difference.signum() == 0) {
            return;
        }

        if (rawHome >= rawDraw && rawHome >= rawAway) {
            home.setScale(PROBABILITY_SCALE, RoundingMode.HALF_UP);
            // BigDecimal is immutable; adjustment is applied by the caller's mutable holder below.
        }
    }

    private String determineResult(double homeProbability, double drawProbability, double awayProbability) {
        if (homeProbability > drawProbability && homeProbability > awayProbability) {
            return "HOME";
        }
        if (awayProbability > drawProbability && awayProbability > homeProbability) {
            return "AWAY";
        }
        return "DRAW";
    }

    private MatchPrediction insufficientDataPrediction(Match match) {
        return MatchPrediction.builder()
                .match(match)
                .predictionStatus("INSUFFICIENT_DATA")
                .predictionReasoning("Not enough valid historical score or xG data to calculate a reliable prediction.")
                .calculatedAt(Instant.now())
                .build();
    }

    private record PredictionSample(String engineName, MatchPrediction prediction, double weight,
                                    double homeProbability, double drawProbability, double awayProbability) {
        private BigDecimal homeXg() {
            return prediction.getPredictedHomeXg();
        }

        private BigDecimal awayXg() {
            return prediction.getPredictedAwayXg();
        }
    }
}
