package com.mmo.module.fb.analytics.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DixonColesProbabilityCalculatorTest {

    private static final double TOLERANCE = 1.0e-10;

    @Test
    void zeroCorrectionParameterMatchesIndependentPoissonModel() {
        PoissonProbabilityCalculator.Result poisson =
                PoissonProbabilityCalculator.calculate(1.45, 1.10);
        DixonColesProbabilityCalculator.Result dixonColes =
                DixonColesProbabilityCalculator.calculate(1.45, 1.10, 0.0);

        assertEquals(poisson.homeWin(), dixonColes.homeWin(), TOLERANCE);
        assertEquals(poisson.draw(), dixonColes.draw(), TOLERANCE);
        assertEquals(poisson.awayWin(), dixonColes.awayWin(), TOLERANCE);
    }

    @Test
    void equalExpectedGoalsKeepHomeAndAwayProbabilitiesSymmetric() {
        DixonColesProbabilityCalculator.Result result =
                DixonColesProbabilityCalculator.calculate(1.6, 1.6, -0.08);

        assertEquals(result.homeWin(), result.awayWin(), TOLERANCE);
        assertProbabilityDistribution(result);
    }

    @Test
    void lowScoreAdjustmentProducesNormalizedProbabilities() {
        DixonColesProbabilityCalculator.Result result =
                DixonColesProbabilityCalculator.calculate(1.7, 1.2, -0.12);

        assertProbabilityDistribution(result);
    }

    @Test
    void rejectsNonFiniteCorrectionParameter() {
        assertThrows(IllegalArgumentException.class,
                () -> DixonColesProbabilityCalculator.calculate(1.2, 1.0, Double.NaN));
    }

    @Test
    void rejectsCorrectionParameterThatMakesAdjustmentNonPositive() {
        assertThrows(IllegalArgumentException.class,
                () -> DixonColesProbabilityCalculator.calculate(2.0, 1.5, 1.0));
    }

    @Test
    void rejectsInvalidExpectedGoals() {
        assertThrows(IllegalArgumentException.class,
                () -> DixonColesProbabilityCalculator.calculate(-0.1, 1.0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> DixonColesProbabilityCalculator.calculate(1.0, 100.1, 0.0));
    }

    private static void assertProbabilityDistribution(DixonColesProbabilityCalculator.Result result) {
        assertTrue(Double.isFinite(result.homeWin()) && result.homeWin() >= 0.0 && result.homeWin() <= 1.0);
        assertTrue(Double.isFinite(result.draw()) && result.draw() >= 0.0 && result.draw() <= 1.0);
        assertTrue(Double.isFinite(result.awayWin()) && result.awayWin() >= 0.0 && result.awayWin() <= 1.0);
        assertEquals(1.0, result.total(), TOLERANCE);
    }
}
