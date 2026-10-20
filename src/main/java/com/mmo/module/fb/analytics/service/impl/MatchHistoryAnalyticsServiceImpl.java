package com.mmo.module.fb.analytics.service.impl;

import com.mmo.module.fb.analytics.service.MatchHistoryAnalyticsService;
import com.mmo.module.fb.analytics.service.MatchHistoryAnalyticsService.ExpectedGoals;
import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.entity.Team;
import com.mmo.module.fb.domain.enums.MatchStatus;
import com.mmo.module.fb.domain.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MatchHistoryAnalyticsServiceImpl implements MatchHistoryAnalyticsService {

    private static final int TEAM_HISTORY_SIZE = 20;
    private static final int LEAGUE_BASELINE_SIZE = 100;
    private static final int MIN_VENUE_SAMPLES = 3;
    private static final int MIN_OVERALL_SAMPLES = 5;
    private static final int MIN_LEAGUE_BASELINE_SAMPLES = 10;
    // Prior pseudo-matches stabilize estimates when a team has little usable history.
    // Tune this parameter using walk-forward out-of-sample evaluation.
    private static final double PRIOR_STRENGTH = 5.0;
    private static final double TEAM_RECENCY_DECAY = 0.92;
    private static final double LEAGUE_RECENCY_DECAY = 0.99;
    private static final double MIN_EXPECTED_GOALS = 0.15;
    private static final double MAX_EXPECTED_GOALS = 4.50;

    private final MatchRepository matchRepository;

    @Override
    public Optional<ExpectedGoals> estimateFromGoals(Match match) {
        return estimate(match, false);
    }

    @Override
    public Optional<ExpectedGoals> estimateFromExpectedGoals(Match match) {
        return estimate(match, true);
    }

    private Optional<ExpectedGoals> estimate(Match match, boolean useExpectedGoals) {
        if (!hasTeamsAndLeague(match)) {
            return Optional.empty();
        }

        Long leagueId = match.getSeason().getLeague().getId();
        TeamHistory home = loadTeamHistory(match.getHomeTeam(), leagueId, match.getMatchTime());
        TeamHistory away = loadTeamHistory(match.getAwayTeam(), leagueId, match.getMatchTime());
        List<Match> leagueMatches = loadLeagueHistory(leagueId, match.getMatchTime());

        Metric attackMetric = useExpectedGoals ? Metric.XG_FOR : Metric.GOALS_FOR;
        Metric defenceMetric = useExpectedGoals ? Metric.XG_AGAINST : Metric.GOALS_AGAINST;
        Rate rawHomeAttack = chooseTeamRate(home, true, attackMetric);
        Rate rawHomeDefence = chooseTeamRate(home, true, defenceMetric);
        Rate rawAwayAttack = chooseTeamRate(away, false, attackMetric);
        Rate rawAwayDefence = chooseTeamRate(away, false, defenceMetric);
        Rate leagueHomeBaseline = calculateLeagueAverage(leagueMatches, true, useExpectedGoals);
        Rate leagueAwayBaseline = calculateLeagueAverage(leagueMatches, false, useExpectedGoals);

        if (rawHomeAttack == null || rawHomeDefence == null || rawAwayAttack == null || rawAwayDefence == null
                || !validLeagueBaseline(leagueHomeBaseline) || !validLeagueBaseline(leagueAwayBaseline)) {
            return Optional.empty();
        }

        // Shrink sparse team rates toward the venue-specific competition baseline.
        // Home goals-for and away goals-against share the home-scoring baseline;
        // away goals-for and home goals-against share the away-scoring baseline.
        Rate homeAttack = shrink(rawHomeAttack, leagueHomeBaseline);
        Rate awayDefence = shrink(rawAwayDefence, leagueHomeBaseline);
        Rate awayAttack = shrink(rawAwayAttack, leagueAwayBaseline);
        Rate homeDefence = shrink(rawHomeDefence, leagueAwayBaseline);

        double expectedHome = homeAttack.value() * awayDefence.value() / leagueHomeBaseline.value();
        double expectedAway = awayAttack.value() * homeDefence.value() / leagueAwayBaseline.value();

        return Optional.of(new ExpectedGoals(
                clampExpectedGoals(expectedHome),
                clampExpectedGoals(expectedAway),
                Math.min(Math.min(homeAttack.samples(), awayDefence.samples()), leagueHomeBaseline.samples()),
                Math.min(Math.min(awayAttack.samples(), homeDefence.samples()), leagueAwayBaseline.samples())));
    }

    private TeamHistory loadTeamHistory(Team team, Long leagueId, java.time.Instant before) {
        List<Match> matches = matchRepository.findRecentFinishedMatchesForTeamInLeagueBefore(
                team.getId(), leagueId, MatchStatus.FINISHED, before, PageRequest.of(0, TEAM_HISTORY_SIZE));
        return new TeamHistory(team, matches == null ? List.of() : matches);
    }

    private List<Match> loadLeagueHistory(Long leagueId, java.time.Instant before) {
        List<Match> matches = matchRepository.findRecentFinishedMatchesInLeagueBefore(
                leagueId, MatchStatus.FINISHED, before, PageRequest.of(0, LEAGUE_BASELINE_SIZE));
        return matches == null ? List.of() : matches;
    }

    private Rate chooseTeamRate(TeamHistory history, boolean homeVenue, Metric metric) {
        Rate venueRate = calculateTeamRate(history, homeVenue, metric);
        if (venueRate.samples() >= MIN_VENUE_SAMPLES) {
            return venueRate;
        }

        Rate overallRate = calculateTeamRate(history, null, metric);
        return overallRate.samples() >= MIN_OVERALL_SAMPLES ? overallRate : null;
    }

    private Rate calculateTeamRate(TeamHistory history, Boolean homeVenue, Metric metric) {
        double weightedTotal = 0;
        double totalWeight = 0;
        double squaredWeightTotal = 0;
        int validSamples = 0;

        // Keep the original match index for recency weights. Missing xG must not make
        // an older observation appear artificially recent.
        for (int i = 0; i < history.matches().size(); i++) {
            Match match = history.matches().get(i);
            boolean teamWasHome = match.getHomeTeam().getId().equals(history.team().getId());
            if (homeVenue != null && teamWasHome != homeVenue) {
                continue;
            }

            Double value = metric.value(match, teamWasHome);
            if (!isValidMetric(value)) {
                continue;
            }

            double weight = Math.pow(TEAM_RECENCY_DECAY, i);
            weightedTotal += value * weight;
            totalWeight += weight;
            squaredWeightTotal += weight * weight;
            validSamples++;
        }

        return rate(weightedTotal, totalWeight, squaredWeightTotal, validSamples);
    }

    private Rate calculateLeagueAverage(List<Match> matches, boolean homeSide, boolean xg) {
        double weightedTotal = 0;
        double totalWeight = 0;
        double squaredWeightTotal = 0;
        int validSamples = 0;

        for (int i = 0; i < matches.size(); i++) {
            Match match = matches.get(i);
            Double value;
            if (xg) {
                value = homeSide ? match.getHomeXG() : match.getAwayXG();
            } else {
                Integer score = homeSide ? match.getHomeScore() : match.getAwayScore();
                value = score == null ? null : score.doubleValue();
            }

            if (!isValidMetric(value)) {
                continue;
            }

            double weight = Math.pow(LEAGUE_RECENCY_DECAY, i);
            weightedTotal += value * weight;
            totalWeight += weight;
            squaredWeightTotal += weight * weight;
            validSamples++;
        }

        return rate(weightedTotal, totalWeight, squaredWeightTotal, validSamples);
    }

    private Rate rate(double weightedTotal, double totalWeight, double squaredWeightTotal, int samples) {
        if (totalWeight <= 0 || squaredWeightTotal <= 0) {
            return new Rate(0, 0, 0);
        }
        double effectiveSamples = totalWeight * totalWeight / squaredWeightTotal;
        return new Rate(weightedTotal / totalWeight, samples, effectiveSamples);
    }

    private Rate shrink(Rate estimate, Rate baseline) {
        double evidenceWeight = estimate.effectiveSamples()
                / (estimate.effectiveSamples() + PRIOR_STRENGTH);
        double value = evidenceWeight * estimate.value() + (1.0 - evidenceWeight) * baseline.value();
        return new Rate(value, estimate.samples(), estimate.effectiveSamples());
    }

    private boolean validLeagueBaseline(Rate rate) {
        return rate != null && rate.samples() >= MIN_LEAGUE_BASELINE_SAMPLES
                && Double.isFinite(rate.value()) && rate.value() > 0;
    }

    private boolean isValidMetric(Double value) {
        return value != null && Double.isFinite(value) && value >= 0;
    }

    private double clampExpectedGoals(double expectedGoals) {
        if (!Double.isFinite(expectedGoals)) {
            throw new IllegalArgumentException("Calculated expected goals are not finite");
        }
        return Math.max(MIN_EXPECTED_GOALS, Math.min(MAX_EXPECTED_GOALS, expectedGoals));
    }

    private boolean hasTeamsAndLeague(Match match) {
        return match != null
                && match.getHomeTeam() != null
                && match.getAwayTeam() != null
                && match.getHomeTeam().getId() != null
                && match.getAwayTeam().getId() != null
                && !match.getHomeTeam().getId().equals(match.getAwayTeam().getId())
                && match.getSeason() != null
                && match.getSeason().getLeague() != null
                && match.getSeason().getLeague().getId() != null;
    }

    private record TeamHistory(Team team, List<Match> matches) {
    }

    private record Rate(double value, int samples, double effectiveSamples) {
    }

    private enum Metric {
        GOALS_FOR {
            @Override Double value(Match match, boolean teamWasHome) {
                Integer value = teamWasHome ? match.getHomeScore() : match.getAwayScore();
                return value == null ? null : value.doubleValue();
            }
        },
        GOALS_AGAINST {
            @Override Double value(Match match, boolean teamWasHome) {
                Integer value = teamWasHome ? match.getAwayScore() : match.getHomeScore();
                return value == null ? null : value.doubleValue();
            }
        },
        XG_FOR {
            @Override Double value(Match match, boolean teamWasHome) {
                return teamWasHome ? match.getHomeXG() : match.getAwayXG();
            }
        },
        XG_AGAINST {
            @Override Double value(Match match, boolean teamWasHome) {
                return teamWasHome ? match.getAwayXG() : match.getHomeXG();
            }
        };

        abstract Double value(Match match, boolean teamWasHome);
    }
}
