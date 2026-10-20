package com.mmo.module.fb.analytics.engine;

public final class PoissonProbabilityCalculator {

    private static final double TAIL_TOLERANCE = 1.0e-12;
    private static final int MAX_GOALS_SUPPORTED = 512;
    private static final double MAX_EXPECTED_GOALS = 100.0;

    private PoissonProbabilityCalculator() {
    }

    public static Result calculate(double homeExpectedGoals, double awayExpectedGoals) {
        if (!validExpectedGoals(homeExpectedGoals) || !validExpectedGoals(awayExpectedGoals)) {
            throw new IllegalArgumentException(
                    "Expected goals must be finite and between 0 and " + MAX_EXPECTED_GOALS);
        }

        double[] home = distribution(homeExpectedGoals);
        double[] away = distribution(awayExpectedGoals);
        double homeWin = 0;
        double draw = 0;
        double awayWin = 0;

        for (int homeGoals = 0; homeGoals < home.length; homeGoals++) {
            for (int awayGoals = 0; awayGoals < away.length; awayGoals++) {
                double probability = home[homeGoals] * away[awayGoals];
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
        if (total <= 0 || !Double.isFinite(total)) {
            throw new IllegalStateException("Poisson score matrix produced an invalid probability total");
        }

        // The omitted Poisson tail is bounded by TAIL_TOLERANCE per team. Normalize
        // the tiny floating-point/truncation remainder to keep 1X2 probabilities coherent.
        return new Result(homeWin / total, draw / total, awayWin / total);
    }

    /**
     * Builds a Poisson PMF until its cumulative mass reaches 1 - TAIL_TOLERANCE.
     * This avoids the systematic loss of high-score outcomes from a fixed 0..12 grid.
     */
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

        return java.util.Arrays.copyOf(probabilities, lastGoal + 1);
    }

    private static boolean validExpectedGoals(double value) {
        return Double.isFinite(value) && value >= 0.0 && value <= MAX_EXPECTED_GOALS;
    }

    public record Result(double homeWin, double draw, double awayWin) {
    }
}
