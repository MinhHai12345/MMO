package com.mmo.module.fb.analytics.engine;

final class PoissonProbabilityCalculator {

    private static final int MAX_GOALS = 12;

    private PoissonProbabilityCalculator() {
    }

    static Result calculate(double homeExpectedGoals, double awayExpectedGoals) {
        if (!validExpectedGoals(homeExpectedGoals) || !validExpectedGoals(awayExpectedGoals)) {
            throw new IllegalArgumentException("Expected goals must be finite and non-negative");
        }

        double[] home = distribution(homeExpectedGoals);
        double[] away = distribution(awayExpectedGoals);
        double homeWin = 0;
        double draw = 0;
        double awayWin = 0;

        for (int homeGoals = 0; homeGoals <= MAX_GOALS; homeGoals++) {
            for (int awayGoals = 0; awayGoals <= MAX_GOALS; awayGoals++) {
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

        return new Result(homeWin / total, draw / total, awayWin / total);
    }

    private static double[] distribution(double lambda) {
        double[] probabilities = new double[MAX_GOALS + 1];
        probabilities[0] = Math.exp(-lambda);

        for (int goals = 1; goals <= MAX_GOALS; goals++) {
            probabilities[goals] = probabilities[goals - 1] * lambda / goals;
        }
        return probabilities;
    }

    private static boolean validExpectedGoals(double value) {
        return Double.isFinite(value) && value >= 0;
    }

    record Result(double homeWin, double draw, double awayWin) {
    }
}
