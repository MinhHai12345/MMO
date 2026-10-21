package com.mmo.module.fb.analytics.engine;

import com.mmo.module.fb.analytics.service.TeamFormFeatureService;
import com.mmo.module.fb.analytics.service.TeamFormFeatureService.MatchFeatures;
import com.mmo.module.fb.analytics.service.TeamFormFeatureService.TeamFeatures;
import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.entity.MatchPrediction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;

/**
 * An independent, explainable goals-form model. It is deliberately kept separate
 * from the historical Poisson models so the ensemble can ignore it when history
 * is too sparse.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TeamFormPredictionEngine implements PredictionEngine {

    private static final int MIN_OVERALL_MATCHES = 5;
    private static final int MIN_VENUE_MATCHES = 3;
    private static final int PROBABILITY_SCALE = 6;
    private static final double MIN_EXPECTED_GOALS = 0.15;
    private static final double MAX_EXPECTED_GOALS = 4.50;
    private static final double MODEL_WEIGHT = 0.20;

    private final TeamFormFeatureService teamFormFeatureService;

    @Override
    public MatchPrediction predict(Match match) {
        Optional<MatchFeatures> optionalFeatures = teamFormFeatureService.buildFeatures(match);
        if (optionalFeatures.isEmpty()) {
            return null;
        }

        MatchFeatures features = optionalFeatures.get();
        TeamFeatures home = features.home();
        TeamFeatures away = features.away();

        if (home.matchesUsed() < MIN_OVERALL_MATCHES || away.matchesUsed() < MIN_OVERALL_MATCHES) {
            log.debug("Insufficient team-form history for matchId={}, homeSamples={}, awaySamples={}",
                    match == null ? null : match.getId(), home.matchesUsed(), away.matchesUsed());
            return null;
        }

        double expectedHomeGoals = estimateExpectedGoals(
                home, away);
        double expectedAwayGoals = estimateExpectedGoals(
                away, home);

        if (!Double.isFinite(expectedHomeGoals) || !Double.isFinite(expectedAwayGoals)) {
            return null;
        }

        PoissonProbabilityCalculator.Result probabilities =
                PoissonProbabilityCalculator.calculate(expectedHomeGoals, expectedAwayGoals);

        return MatchPrediction.builder()
                .match(match)
                // These values are goals-based expectations, not provider xG; keep xG fields empty.
                .predictedHomeXg(null)
                .predictedAwayXg(null)
                .winHomeProb(probability(probabilities.homeWin()))
                .drawProb(probability(probabilities.draw()))
                .winAwayProb(probability(probabilities.awayWin()))
                .predictedResult(determineResult(probabilities))
                .predictionStatus("PENDING")
                .confidenceScore(BigDecimal.valueOf(Math.max(probabilities.homeWin(),
                                Math.max(probabilities.draw(), probabilities.awayWin())))
                        .setScale(2, RoundingMode.HALF_UP))
                .calculatedAt(Instant.now())
                .predictionReasoning(String.format(
                        "Team-form Poisson model; expected goals %.2f-%.2f; overall samples home=%d away=%d; venue samples home=%d away=%d",
                        expectedHomeGoals, expectedAwayGoals, home.matchesUsed(), away.matchesUsed(),
                        home.venueMatchesUsed(), away.venueMatchesUsed()))
                .build();
    }

    private double estimateExpectedGoals(TeamFeatures attackingTeam, TeamFeatures defendingTeam) {
        double goalsFor = attackingTeam.averageGoalsFor();
        double opponentGoalsAgainst = defendingTeam.averageGoalsAgainst();

        if (attackingTeam.venueMatchesUsed() >= MIN_VENUE_MATCHES
                && defendingTeam.venueMatchesUsed() >= MIN_VENUE_MATCHES) {
            goalsFor = attackingTeam.venueAverageGoalsFor();
            opponentGoalsAgainst = defendingTeam.venueAverageGoalsAgainst();
        }

        // Equal blend avoids letting one noisy rate dominate when the history is small.
        double estimate = (goalsFor + opponentGoalsAgainst) / 2.0;
        return Math.max(MIN_EXPECTED_GOALS, Math.min(MAX_EXPECTED_GOALS, estimate));
    }

    private BigDecimal probability(double value) {
        return BigDecimal.valueOf(value).setScale(PROBABILITY_SCALE, RoundingMode.HALF_UP);
    }

    private String determineResult(PoissonProbabilityCalculator.Result probabilities) {
        if (probabilities.homeWin() > probabilities.draw()
                && probabilities.homeWin() > probabilities.awayWin()) {
            return "HOME";
        }
        if (probabilities.awayWin() > probabilities.draw()
                && probabilities.awayWin() > probabilities.homeWin()) {
            return "AWAY";
        }
        return "DRAW";
    }

    @Override
    public String getEngineName() {
        return "TEAM_FORM";
    }

    @Override
    public double getWeight() {
        return MODEL_WEIGHT;
    }
}
