package com.mmo.module.fb.analytics.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DixonColesProbabilityCalculatorTest {

    @Test
    void returnsNormalizedProbabilitiesForValidExpectedGoals() {
        DixonColesProbabilityCalculator.Result result =
                DixonColesProbabilityCalculator.calculate(1.55, 1.10, -0.08);

        assertTrue(result.homeWin() > 0.0);
        assertTrue(result.draw() > 0.0);
        assertTrue(result.awayWin() > 0.0);
        assertEquals(1.0, result.total(), 1.0e-12);
    }

    @Test
    void rhoZeroMatchesIndependentPoissonExactly() {
        DixonColesProbabilityCalculator.Result result =
                DixonColesProbabilityCalculator.calculate(1.45, 0.95, 0.0);

        PoissonProbabilityCalculator.Result independent =
                PoissonProbabilityCalculator.calculate(1.45, 0.95);

        assertEquals(independent.homeWin(), result.homeWin(), 1.0e-12);
        assertEquals(independent.draw(), result.draw(), 1.0e-12);
        assertEquals(independent.awayWin(), result.awayWin(), 1.0e-12);
    }

    @Test
    void rejectsInvalidExpectedGoalsAndInvalidLowScoreCorrection() {
        assertThrows(IllegalArgumentException.class,
                () -> DixonColesProbabilityCalculator.calculate(-0.1, 1.0, -0.1));
        assertThrows(IllegalArgumentException.class,
                () -> DixonColesProbabilityCalculator.calculate(1.0, 1.0, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> DixonColesProbabilityCalculator.calculate(10.0, 10.0, -0.1));
    }

    @Test
    void preservesSymmetryWhenExpectedGoalsAreEqual() {
        DixonColesProbabilityCalculator.Result result =
                DixonColesProbabilityCalculator.calculate(1.2, 1.2, -0.1);

        assertEquals(result.homeWin(), result.awayWin(), 1.0e-12);
    }
}
