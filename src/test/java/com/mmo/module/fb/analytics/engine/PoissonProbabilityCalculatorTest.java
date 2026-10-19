package com.mmo.module.fb.analytics.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void adaptiveTailSupportsHighScoringMatchesWithoutFixedTwelveGoalCutoff() {
        PoissonProbabilityCalculator.Result result = PoissonProbabilityCalculator.calculate(20.0, 2.0);

        assertTrue(result.homeWin() > 0.999999);
        assertTrue(result.draw() >= 0.0);
        assertTrue(result.awayWin() >= 0.0);
        assertEquals(1.0, result.homeWin() + result.draw() + result.awayWin(), 1.0e-12);
    }

    @Test
    void zeroExpectedGoalsProducesDeterministicZeroScoreForThatTeam() {
        PoissonProbabilityCalculator.Result result = PoissonProbabilityCalculator.calculate(0.0, 1.5);

        assertEquals(0.0, result.homeWin(), 1.0e-12);
        assertEquals(Math.exp(-1.5), result.draw(), 1.0e-12);
        assertEquals(1.0 - Math.exp(-1.5), result.awayWin(), 1.0e-12);
    }

    @Test
    void rejectsInvalidExpectedGoals() {
        assertThrows(IllegalArgumentException.class,
                () -> PoissonProbabilityCalculator.calculate(Double.NaN, 1.2));
        assertThrows(IllegalArgumentException.class,
                () -> PoissonProbabilityCalculator.calculate(1.2, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,
                () -> PoissonProbabilityCalculator.calculate(100.1, 1.2));
    }
}
