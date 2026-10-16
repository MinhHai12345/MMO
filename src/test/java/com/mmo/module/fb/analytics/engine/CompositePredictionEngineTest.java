package com.mmo.module.fb.analytics.engine;

import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.entity.MatchPrediction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompositePredictionEngineTest {

    @Test
    void shouldExcludeCompositeEngineToPreventRecursivePrediction() {
        PredictionEngine compositeBean = mock(PredictionEngine.class);
        when(compositeBean.getEngineName()).thenReturn("COMPOSITE");
        PredictionEngine baseEngine = engine("BASE", 1.0, prediction(0.5, 0.3, 0.2, 1.4, 0.9));

        CompositePredictionEngine composite = new CompositePredictionEngine(List.of(compositeBean, baseEngine));
        Match match = Match.builder().build();

        MatchPrediction result = composite.predict(match);

        verify(compositeBean, never()).predict(any());
        verify(baseEngine).predict(match);
        assertEquals("HOME", result.getPredictedResult());
    }

    @Test
    void shouldAggregatePredictionsUsingEngineWeightsAndNormalizeProbabilities() {
        PredictionEngine first = engine("FIRST", 1.0, prediction(0.8, 0.1, 0.1, 2.0, 1.0));
        PredictionEngine second = engine("SECOND", 3.0, prediction(0.2, 0.3, 0.5, 1.0, 2.0));

        CompositePredictionEngine composite = new CompositePredictionEngine(List.of(first, second));
        Match match = Match.builder().build();

        MatchPrediction result = composite.predict(match);

        assertEquals(0, new BigDecimal("0.35").compareTo(result.getWinHomeProb()));
        assertEquals(0, new BigDecimal("0.25").compareTo(result.getDrawProb()));
        assertEquals(0, new BigDecimal("0.40").compareTo(result.getWinAwayProb()));
        assertEquals(0, new BigDecimal("1.25").compareTo(result.getPredictedHomeXg()));
        assertEquals(0, new BigDecimal("1.75").compareTo(result.getPredictedAwayXg()));
        assertEquals("AWAY", result.getPredictedResult());
        assertNotNull(result.getCalculatedAt());

        BigDecimal probabilityTotal = result.getWinHomeProb()
                .add(result.getDrawProb())
                .add(result.getWinAwayProb());
        assertEquals(0, BigDecimal.ONE.compareTo(probabilityTotal));
    }

    @Test
    void shouldReturnDefaultPredictionWhenNoUsableEngineExists() {
        PredictionEngine zeroWeightEngine = engine("ZERO", 0.0, prediction(0.9, 0.05, 0.05, 3.0, 0.2));
        PredictionEngine nullResultEngine = engine("NULL", 1.0, null);

        CompositePredictionEngine composite = new CompositePredictionEngine(
                List.of(zeroWeightEngine, nullResultEngine));
        Match match = Match.builder().build();

        MatchPrediction result = composite.predict(match);

        assertEquals("PENDING", result.getPredictionStatus());
        assertEquals("DRAW", result.getPredictedResult());
        assertEquals(0, new BigDecimal("0.33").compareTo(result.getWinHomeProb()));
        assertEquals(0, new BigDecimal("0.34").compareTo(result.getDrawProb()));
        assertEquals(0, new BigDecimal("0.33").compareTo(result.getWinAwayProb()));
        assertNotNull(result.getCalculatedAt());
    }

    private PredictionEngine engine(String name, double weight, MatchPrediction result) {
        PredictionEngine engine = mock(PredictionEngine.class);
        when(engine.getEngineName()).thenReturn(name);
        when(engine.getWeight()).thenReturn(weight);
        when(engine.predict(any(Match.class))).thenReturn(result);
        return engine;
    }

    private MatchPrediction prediction(double home, double draw, double away, double homeXg, double awayXg) {
        return MatchPrediction.builder()
                .winHomeProb(BigDecimal.valueOf(home))
                .drawProb(BigDecimal.valueOf(draw))
                .winAwayProb(BigDecimal.valueOf(away))
                .predictedHomeXg(BigDecimal.valueOf(homeXg))
                .predictedAwayXg(BigDecimal.valueOf(awayXg))
                .build();
    }
}
