package com.mmo.module.fb.analytics.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoissonProbabilityCalculatorTest {

    private static final double TOLERANCE = 1.0e-10;

    @Test
    void zeroExpectedGoalsProducesCertainGoallessDraw() {
        PoissonProbabilityCalculator.Result result = PoissonProbabilityCalculator.calculate(0.0, 0.0);

        assertEquals(0.0, result.homeWin(), TOLERANCE);
        assertEquals(1.0, result.draw(), TOLERANCE);
        assertEquals(0.0, result.awayWin(), TOLERANCE);
    }

    @Test
    void equalExpectedGoalsProduceSymmetricOutcomeProbabilities() {
        PoissonProbabilityCalculator.Result result = PoissonProbabilityCalculator.calculate(1.7, 1.7);

        assertEquals(result.homeWin(), result.awayWin(), TOLERANCE);
        assertProbabilityDistribution(result.homeWin(), result.draw(), result.awayWin());
    }

    @Test
    void normalizesProbabilitiesForHighExpectedGoals() {
        PoissonProbabilityCalculator.Result result = PoissonProbabilityCalculator.calculate(35.0, 42.0);

        assertProbabilityDistribution(result.homeWin(), result.draw(), result.awayWin());
    }

    @Test
    void rejectsNegativeExpectedGoals() {
        assertThrows(IllegalArgumentException.class,
                () -> PoissonProbabilityCalculator.calculate(-0.01, 1.0));
    }

    @Test
    void rejectsNonFiniteExpectedGoals() {
        assertThrows(IllegalArgumentException.class,
                () -> PoissonProbabilityCalculator.calculate(Double.NaN, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> PoissonProbabilityCalculator.calculate(1.0, Double.POSITIVE_INFINITY));
    }

    @Test
    void rejectsExpectedGoalsAboveSupportedLimit() {
        assertThrows(IllegalArgumentException.class,
                () -> PoissonProbabilityCalculator.calculate(100.01, 1.0));
    }

    private static void assertProbabilityDistribution(double home, double draw, double away) {
        assertTrue(Double.isFinite(home) && home >= 0.0 && home <= 1.0);
        assertTrue(Double.isFinite(draw) && draw >= 0.0 && draw <= 1.0);
        assertTrue(Double.isFinite(away) && away >= 0.0 && away <= 1.0);
        assertEquals(1.0, home + draw + away, TOLERANCE);
    }
}
