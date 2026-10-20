package com.mmo.module.fb.analytics.engine;

import java.util.Arrays;

/**
 * Converts expected-goal rates into 1X2 probabilities using the Dixon-Coles
 * low-score correction. The rho parameter must be estimated from historical
 * training data before using this model for production predictions.
 */
public final class DixonColesProbabilityCalculator {

    private static final double TAIL_TOLERANCE = 1.0e-12;
    private static final int MAX_GOALS_SUPPORTED = 512;
    private static final double MAX_EXPECTED_GOALS = 100.0;

    private DixonColesProbabilityCalculator() {
    }

    public static Result calculate(double homeExpectedGoals, double awayExpectedGoals, double rho) {
        validateExpectedGoals(homeExpectedGoals, "homeExpectedGoals");
        validateExpectedGoals(awayExpectedGoals, "awayExpectedGoals");
        if (!Double.isFinite(rho)) {
            throw new IllegalArgumentException("rho must be finite");
        }

        double tau00 = 1.0 - homeExpectedGoals * awayExpectedGoals * rho;
        double tau01 = 1.0 + homeExpectedGoals * rho;
        double tau10 = 1.0 + awayExpectedGoals * rho;
        double tau11 = 1.0 - rho;
        if (!(tau00 > 0.0) || !(tau01 > 0.0) || !(tau10 > 0.0) || !(tau11 > 0.0)) {
            throw new IllegalArgumentException("rho makes a Dixon-Coles low-score adjustment non-positive");
        }

        double[] home = distribution(homeExpectedGoals);
        double[] away = distribution(awayExpectedGoals);
        double homeWin = 0.0;
        double draw = 0.0;
        double awayWin = 0.0;

        for (int homeGoals = 0; homeGoals < home.length; homeGoals++) {
            for (int awayGoals = 0; awayGoals < away.length; awayGoals++) {
                double correction = correction(homeGoals, awayGoals, tau00, tau01, tau10, tau11);
                double probability = home[homeGoals] * away[awayGoals] * correction;
                if (homeGoals > awayGoals) {
                    homeWin += probability;
                } else if (homeGoals == awayGoals) {
                    draw += probability;
                } else {
                    awayWin += probability;
                }
            }
        }

        double total = homeWin + draw + awayWin;
        if (!Double.isFinite(total) || total <= 0.0) {
            throw new IllegalStateException("Dixon-Coles score matrix produced invalid probabilities");
        }
        return new Result(homeWin / total, draw / total, awayWin / total);
    }

    private static double correction(int homeGoals, int awayGoals,
                                     double tau00, double tau01, double tau10, double tau11) {
        if (homeGoals == 0 && awayGoals == 0) {
            return tau00;
        }
        if (homeGoals == 0 && awayGoals == 1) {
            return tau01;
        }
        if (homeGoals == 1 && awayGoals == 0) {
            return tau10;
        }
        if (homeGoals == 1 && awayGoals == 1) {
            return tau11;
        }
        return 1.0;
    }

    private static double[] distribution(double lambda) {
        double[] probabilities = new double[MAX_GOALS_SUPPORTED + 1];
        probabilities[0] = Math.exp(-lambda);
        double cumulative = probabilities[0];
        int lastGoal = 0;
        while (cumulative < 1.0 - TAIL_TOLERANCE && lastGoal < MAX_GOALS_SUPPORTED) {
            int nextGoal = lastGoal + 1;
            probabilities[nextGoal] = probabilities[lastGoal] * lambda / nextGoal;
            cumulative += probabilities[nextGoal];
            lastGoal = nextGoal;
        }
        if (cumulative < 1.0 - TAIL_TOLERANCE) {
            throw new IllegalStateException("Could not bound Poisson tail for expected goals " + lambda);
        }
        return Arrays.copyOf(probabilities, lastGoal + 1);
    }

    private static void validateExpectedGoals(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > MAX_EXPECTED_GOALS) {
            throw new IllegalArgumentException(name + " must be finite and between 0 and " + MAX_EXPECTED_GOALS);
        }
    }

    public record Result(double homeWin, double draw, double awayWin) {
        public double total() {
            return homeWin + draw + awayWin;
        }
    }
}
