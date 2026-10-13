package com.mmo.module.fb.analytics.engine;

import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.entity.MatchPrediction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CompositePredictionEngine implements PredictionEngine {

    private final List<PredictionEngine> engines;

    @Override
    public MatchPrediction predict(Match match) {
        log.info("🔮 Running composite prediction for match {}", match.getId());

        List<MatchPrediction> predictions = engines.stream()
                .map(engine -> engine.predict(match))
                .toList();

        if (predictions.isEmpty()) {
            log.warn("⚠️ No prediction engines available");
            return createDefaultPrediction(match);
        }

        return aggregatePredictions(match, predictions);
    }

    @Override
    public String getEngineName() {
        return "COMPOSITE";
    }

    @Override
    public double getWeight() {
        return 1.0;
    }

    private MatchPrediction aggregatePredictions(Match match, List<MatchPrediction> predictions) {
        double totalWeight = engines.stream().mapToDouble(PredictionEngine::getWeight).sum();

        double weightedHomeWinProb = predictions.stream()
                .mapToDouble(p -> p.getWinHomeProb() != null ? p.getWinHomeProb().doubleValue() : 0)
                .sum() / predictions.size();

        double weightedDrawProb = predictions.stream()
                .mapToDouble(p -> p.getDrawProb() != null ? p.getDrawProb().doubleValue() : 0)
                .sum() / predictions.size();

        double weightedAwayWinProb = predictions.stream()
                .mapToDouble(p -> p.getWinAwayProb() != null ? p.getWinAwayProb().doubleValue() : 0)
                .sum() / predictions.size();

        double weightedHomeXg = predictions.stream()
                .mapToDouble(p -> p.getPredictedHomeXg() != null ? p.getPredictedHomeXg().doubleValue() : 0)
                .sum() / predictions.size();

        double weightedAwayXg = predictions.stream()
                .mapToDouble(p -> p.getPredictedAwayXg() != null ? p.getPredictedAwayXg().doubleValue() : 0)
                .sum() / predictions.size();

        // Determine predicted result
        String predictedResult = determineResult(weightedHomeWinProb, weightedDrawProb, weightedAwayWinProb);

        return MatchPrediction.builder()
                .match(match)
                .predictedHomeXg(new BigDecimal(weightedHomeXg))
                .predictedAwayXg(new BigDecimal(weightedAwayXg))
                .winHomeProb(new BigDecimal(weightedHomeWinProb))
                .drawProb(new BigDecimal(weightedDrawProb))
                .winAwayProb(new BigDecimal(weightedAwayWinProb))
                .predictedResult(predictedResult)
                .predictionStatus("PENDING")
                .confidenceScore(calculateConfidence(weightedHomeWinProb, weightedDrawProb, weightedAwayWinProb))
                .build();
    }

    private String determineResult(double homeProb, double drawProb, double awayProb) {
        if (homeProb > drawProb && homeProb > awayProb) {
            return "HOME";
        } else if (awayProb > drawProb && awayProb > homeProb) {
            return "AWAY";
        }
        return "DRAW";
    }

    private BigDecimal calculateConfidence(double homeProb, double drawProb, double awayProb) {
        double maxProb = Math.max(homeProb, Math.max(drawProb, awayProb));
        return BigDecimal.valueOf(maxProb).setScale(2, RoundingMode.HALF_UP);
    }

    private MatchPrediction createDefaultPrediction(Match match) {
        return MatchPrediction.builder()
                .match(match)
                .predictionStatus("PENDING")
                .winHomeProb(BigDecimal.valueOf(0.33))
                .drawProb(BigDecimal.valueOf(0.34))
                .winAwayProb(BigDecimal.valueOf(0.33))
                .predictedResult("DRAW")
                .confidenceScore(BigDecimal.valueOf(0.33))
                .build();
    }
}
