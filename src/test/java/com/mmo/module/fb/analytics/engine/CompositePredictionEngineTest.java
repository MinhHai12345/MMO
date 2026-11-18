package com.mmo.module.fb.analytics.engine;

import com.mmo.module.fb.analytics.entity.MatchPrediction;
import com.mmo.module.fb.domain.match.entity.Match;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompositePredictionEngineTest {

    private final Match match = new Match();

    @Test
    void combinesModelProbabilitiesUsingRelativeWeightsAndNormalizesRoundedResult() {
        CompositePredictionEngine engine = new CompositePredictionEngine(List.of(
                engine("GOALS", 0.6, prediction(0.6, 0.3, 0.1, null, null)),
                engine("XG", 0.4, prediction(0.2, 0.3, 0.5, null, null))
        ));

        MatchPrediction result = engine.predict(match);

        assertEquals(new BigDecimal("0.44"), result.getWinHomeProb());
        assertEquals(new BigDecimal("0.30"), result.getDrawProb());
        assertEquals(new BigDecimal("0.26"), result.getWinAwayProb());
        assertEquals(new BigDecimal("1.00"),
                result.getWinHomeProb().add(result.getDrawProb()).add(result.getWinAwayProb()));
        assertEquals("HOME", result.getPredictedResult());
        assertEquals("PENDING", result.getPredictionStatus());
    }

    @Test
    void renormalizesEachEngineProbabilityVectorBeforeCombining() {
        CompositePredictionEngine engine = new CompositePredictionEngine(List.of(
                engine("UNSCALED", 1.0, prediction(6.0, 3.0, 1.0, null, null))
        ));

        MatchPrediction result = engine.predict(match);

        assertEquals(new BigDecimal("0.60"), result.getWinHomeProb());
        assertEquals(new BigDecimal("0.30"), result.getDrawProb());
        assertEquals(new BigDecimal("0.10"), result.getWinAwayProb());
    }

    @Test
    void ignoresNullInvalidAndNonPositiveWeightEngines() {
        CompositePredictionEngine engine = new CompositePredictionEngine(List.of(
                engine("INVALID", 1.0, prediction(1.2, 0.2, 0.1, null, null)),
                engine("ZERO_WEIGHT", 0.0, prediction(0.1, 0.2, 0.7, null, null)),
                engine("NO_RESULT", 0.5, null),
                engine("VALID", 0.5, prediction(0.2, 0.5, 0.3, null, null))
        ));

        MatchPrediction result = engine.predict(match);

        assertEquals(new BigDecimal("0.20"), result.getWinHomeProb());
        assertEquals(new BigDecimal("0.50"), result.getDrawProb());
        assertEquals(new BigDecimal("0.30"), result.getWinAwayProb());
    }

    @Test
    void continuesWhenOneIndependentEngineThrows() {
        PredictionEngine failing = new StubEngine("FAIL", 0.8, null) {
            @Override
            public MatchPrediction predict(Match ignored) {
                throw new IllegalStateException("model unavailable");
            }
        };
        CompositePredictionEngine engine = new CompositePredictionEngine(List.of(
                failing,
                engine("AVAILABLE", 0.2, prediction(0.25, 0.5, 0.25, null, null))
        ));

        MatchPrediction result = engine.predict(match);

        assertEquals(new BigDecimal("0.25"), result.getWinHomeProb());
        assertEquals(new BigDecimal("0.50"), result.getDrawProb());
        assertEquals(new BigDecimal("0.25"), result.getWinAwayProb());
    }

    @Test
    void returnsInsufficientDataWhenNoEngineProvidesValidProbabilities() {
        CompositePredictionEngine engine = new CompositePredictionEngine(List.of(
                engine("EMPTY", 0.6, null),
                engine("INVALID", 0.4, prediction(0.0, 0.0, 0.0, null, null))
        ));

        MatchPrediction result = engine.predict(match);

        assertEquals("INSUFFICIENT_DATA", result.getPredictionStatus());
        assertNull(result.getWinHomeProb());
        assertNull(result.getDrawProb());
        assertNotNull(result.getPredictionReasoning());
    }

    @Test
    void averagesExpectedGoalsOnlyAcrossSamplesThatContainValidValues() {
        CompositePredictionEngine engine = new CompositePredictionEngine(List.of(
                engine("WITH_XG", 0.6, prediction(0.5, 0.3, 0.2, "2.00", "1.00")),
                engine("WITHOUT_XG", 0.4, prediction(0.5, 0.3, 0.2, null, null))
        ));

        MatchPrediction result = engine.predict(match);

        assertEquals(new BigDecimal("2.00"), result.getPredictedHomeXg());
        assertEquals(new BigDecimal("1.00"), result.getPredictedAwayXg());
    }

    @Test
    void excludesNestedCompositeEnginesToAvoidDoubleCounting() {
        CompositePredictionEngine engine = new CompositePredictionEngine(List.of(
                engine("COMPOSITE", 100.0, prediction(0.0, 1.0, 0.0, null, null)),
                engine("BASE", 1.0, prediction(1.0, 0.0, 0.0, null, null))
        ));

        MatchPrediction result = engine.predict(match);

        assertEquals(new BigDecimal("1.00"), result.getWinHomeProb());
        assertEquals("HOME", result.getPredictedResult());
    }

    @Test
    void resolvesEqualLargestProbabilitiesAsDraw() {
        CompositePredictionEngine engine = new CompositePredictionEngine(List.of(
                engine("TIED", 1.0, prediction(0.4, 0.4, 0.2, null, null))
        ));

        assertEquals("DRAW", engine.predict(match).getPredictedResult());
    }

    private static PredictionEngine engine(String name, double weight, MatchPrediction result) {
        return new StubEngine(name, weight, result);
    }

    private static MatchPrediction prediction(double home, double draw, double away,
                                              String homeXg, String awayXg) {
        return MatchPrediction.builder()
                .winHomeProb(BigDecimal.valueOf(home))
                .drawProb(BigDecimal.valueOf(draw))
                .winAwayProb(BigDecimal.valueOf(away))
                .predictedHomeXg(homeXg == null ? null : new BigDecimal(homeXg))
                .predictedAwayXg(awayXg == null ? null : new BigDecimal(awayXg))
                .build();
    }

    private static class StubEngine implements PredictionEngine {
        private final String name;
        private final double weight;
        private final MatchPrediction result;

        private StubEngine(String name, double weight, MatchPrediction result) {
            this.name = name;
            this.weight = weight;
            this.result = result;
        }

        @Override
        public MatchPrediction predict(Match ignored) {
            return result;
        }

        @Override
        public String getEngineName() {
            return name;
        }

        @Override
        public double getWeight() {
            return weight;
        }
    }
}
