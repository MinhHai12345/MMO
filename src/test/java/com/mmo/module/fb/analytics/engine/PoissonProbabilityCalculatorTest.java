package com.mmo.module.fb.analytics.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoissonProbabilityCalculatorTest {

    @Test
    void equalExpectedGoalsProduceSymmetricWinProbabilities() {
        PoissonProbabilityCalculator.Result result = PoissonProbabilityCalculator.calculate(1.4, 1.4);

        assertEquals(result.homeWin(), result.awayWin(), 1.0e-12);
        assertEquals(1.0, result.homeWin() + result.draw() + result.awayWin(), 1.0e-12);
    }

    @Test
    void strongerHomeExpectedGoalsIncreaseHomeWinProbability() {
        PoissonProbabilityCalculator.Result balanced = PoissonProbabilityCalculator.calculate(1.2, 1.2);
        PoissonProbabilityCalculator.Result homeFavoured = PoissonProbabilityCalculator.calculate(2.0, 0.9);

        assertTrue(homeFavoured.homeWin() > balanced.homeWin());
        assertTrue(homeFavoured.homeWin() > homeFavoured.awayWin());
        assertEquals(1.0, homeFavoured.homeWin() + homeFavoured.draw() + homeFavoured.awayWin(), 1.0e-12);
    }

    @Test
    void rejectsInvalidExpectedGoals() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> PoissonProbabilityCalculator.calculate(Double.NaN, 1.2));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> PoissonProbabilityCalculator.calculate(1.2, Double.POSITIVE_INFINITY));
    }
}
