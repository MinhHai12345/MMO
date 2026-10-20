package com.mmo.module.fb.analytics.service.impl;

import com.mmo.module.fb.analytics.engine.PoissonProbabilityCalculator;
import com.mmo.module.fb.analytics.service.TeamFormBackTestService;
import com.mmo.module.fb.analytics.service.TeamFormFeatureService;
import com.mmo.module.fb.analytics.service.TeamFormFeatureService.MatchFeatures;
import com.mmo.module.fb.analytics.service.TeamFormFeatureService.TeamFeatures;
import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.enums.MatchStatus;
import com.mmo.module.fb.domain.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamFormBackTestServiceImpl implements TeamFormBackTestService {

    private static final int PAGE_SIZE = 100;
    private static final int MAX_CANDIDATES = 500;
    private static final int MIN_TEAM_HISTORY = 3;
    private static final double EPSILON = 1.0e-12;
    private static final double MIN_EXPECTED_GOALS = 0.05;
    private static final double MAX_EXPECTED_GOALS = 5.0;

    private final MatchRepository matchRepository;
    private final TeamFormFeatureService teamFormFeatureService;

    @Override
    @Transactional(readOnly = true)
    public TeamFormBackTestReport run(Instant fromInclusive, Instant toExclusive) {
        if (fromInclusive == null || toExclusive == null || !fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException("from must be before to");
        }

        List<Match> candidates = new ArrayList<>();
        int page = 0;
        List<Match> batch;
        do {
            int remaining = MAX_CANDIDATES - candidates.size();
            if (remaining <= 0) {
                break;
            }
            batch = matchRepository.findTeamFormBacktestCandidates(
                    fromInclusive, toExclusive, MatchStatus.FINISHED,
                    PageRequest.of(page++, Math.min(PAGE_SIZE, remaining)));
            candidates.addAll(batch);
        } while (batch.size() == PAGE_SIZE && candidates.size() < MAX_CANDIDATES);

        Metrics baseline = new Metrics();
        Metrics teamForm = new Metrics();
        int skipped = 0;
        for (Match match : candidates) {
            MatchFeatures features = teamFormFeatureService.buildFeatures(match).orElse(null);
            if (features == null
                    || features.home().matchesUsed() < MIN_TEAM_HISTORY
                    || features.away().matchesUsed() < MIN_TEAM_HISTORY) {
                skipped++;
                continue;
            }

            double homeExpectedGoals = expectedHomeGoals(features.home(), features.away());
            double awayExpectedGoals = expectedAwayGoals(features.home(), features.away());
            PoissonProbabilityCalculator.Result result =
                    PoissonProbabilityCalculator.calculate(homeExpectedGoals, awayExpectedGoals);
            double[] probabilities = normalize(result.homeWin(), result.draw(), result.awayWin());
            if (probabilities == null) {
                skipped++;
                continue;
            }

            int actualClass = match.getHomeScore() > match.getAwayScore() ? 0
                    : match.getHomeScore().equals(match.getAwayScore()) ? 1 : 2;
            baseline.add(new double[]{1.0 / 3.0, 1.0 / 3.0, 1.0 / 3.0}, actualClass);
            teamForm.add(probabilities, actualClass);
        }

        String status = candidates.isEmpty() ? "NO_CANDIDATES"
                : teamForm.count == 0 ? "INSUFFICIENT_HISTORY"
                : candidates.size() == MAX_CANDIDATES ? "CANDIDATE_LIMIT_REACHED"
                : "OK";
        TeamFormBackTestReport report = new TeamFormBackTestReport(
                fromInclusive, toExclusive, candidates.size(), teamForm.count, skipped,
                baseline.snapshot(), teamForm.snapshot(), status);
        log.info("Team-form walk-forward backtest: from={}, to={}, candidates={}, evaluated={}, skipped={}, " +
                        "baselineBrier={}, teamFormBrier={}, status={}",
                fromInclusive, toExclusive, report.candidateMatches(), report.evaluatedMatches(),
                report.skippedInsufficientHistory(), report.uniformBaseline().brierScore(),
                report.teamFormModel().brierScore(), report.status());
        return report;
    }

    /*
     * This is a deliberately isolated benchmark heuristic, not a live-model adjustment.
     * Inputs are all derived from matches strictly before the target kickoff.
     */
    private double expectedHomeGoals(TeamFeatures home, TeamFeatures away) {
        double homeAttack = home.venueMatchesUsed() > 0
                ? home.venueAverageGoalsFor() : home.averageGoalsFor();
        double awayDefence = away.venueMatchesUsed() > 0
                ? away.venueAverageGoalsAgainst() : away.averageGoalsAgainst();
        return clamp(0.55 * homeAttack + 0.30 * awayDefence + 0.15 * home.averageGoalsFor());
    }

    private double expectedAwayGoals(TeamFeatures home, TeamFeatures away) {
        double awayAttack = away.venueMatchesUsed() > 0
                ? away.venueAverageGoalsFor() : away.averageGoalsFor();
        double homeDefence = home.venueMatchesUsed() > 0
                ? home.venueAverageGoalsAgainst() : home.averageGoalsAgainst();
        return clamp(0.55 * awayAttack + 0.30 * homeDefence + 0.15 * away.averageGoalsFor());
    }

    private double clamp(double value) {
        if (!Double.isFinite(value)) {
            return MIN_EXPECTED_GOALS;
        }
        return Math.max(MIN_EXPECTED_GOALS, Math.min(MAX_EXPECTED_GOALS, value));
    }

    private double[] normalize(double home, double draw, double away) {
        if (!validProbability(home) || !validProbability(draw) || !validProbability(away)) {
            return null;
        }
        double total = home + draw + away;
        if (!Double.isFinite(total) || total <= EPSILON) {
            return null;
        }
        return new double[]{home / total, draw / total, away / total};
    }

    private boolean validProbability(double value) {
        return Double.isFinite(value) && value >= 0.0 && value <= 1.0;
    }

    private static final class Metrics {
        private int count;
        private double brierSum;
        private double logLossSum;
        private int correct;

        private void add(double[] probabilities, int actualClass) {
            double brier = 0.0;
            for (int i = 0; i < probabilities.length; i++) {
                double outcome = i == actualClass ? 1.0 : 0.0;
                double difference = probabilities[i] - outcome;
                brier += difference * difference;
            }
            brierSum += brier;
            logLossSum -= Math.log(Math.max(EPSILON, probabilities[actualClass]));
            int predictedClass = probabilities[0] >= probabilities[1] && probabilities[0] >= probabilities[2] ? 0
                    : probabilities[1] >= probabilities[2] ? 1 : 2;
            if (predictedClass == actualClass) {
                correct++;
            }
            count++;
        }

        private MetricSnapshot snapshot() {
            return new MetricSnapshot(count,
                    count == 0 ? 0.0 : brierSum / count,
                    count == 0 ? 0.0 : logLossSum / count,
                    count == 0 ? 0.0 : (double) correct / count);
        }
    }
}
