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

import java.util.ArrayList;
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
    private static final double TEAM_RECENCY_DECAY = 0.92;
    private static final double LEAGUE_RECENCY_DECAY = 0.99;
    private static final double MIN_EXPECTED_GOALS = 0.15;
    private static final double MAX_EXPECTED_GOALS = 4.50;

    private final MatchRepository matchRepository;

    public Optional<ExpectedGoals> estimateFromGoals(Match match) {
        if (!hasTeamsAndLeague(match)) {
            return Optional.empty();
        }

        Long leagueId = match.getSeason().getLeague().getId();
        TeamHistory home = loadTeamHistory(match.getHomeTeam(), leagueId);
        TeamHistory away = loadTeamHistory(match.getAwayTeam(), leagueId);
        List<Match> leagueMatches = loadLeagueHistory(leagueId);

        Rate homeAttack = chooseTeamRate(home, true, Metric.GOALS_FOR);
        Rate homeDefence = chooseTeamRate(home, true, Metric.GOALS_AGAINST);
        Rate awayAttack = chooseTeamRate(away, false, Metric.GOALS_FOR);
        Rate awayDefence = chooseTeamRate(away, false, Metric.GOALS_AGAINST);

        Rate leagueHomeGoals = calculateLeagueAverage(leagueMatches, true, false);
        Rate leagueAwayGoals = calculateLeagueAverage(leagueMatches, false, false);

        if (homeAttack == null || homeDefence == null || awayAttack == null || awayDefence == null
                || !validLeagueBaseline(leagueHomeGoals) || !validLeagueBaseline(leagueAwayGoals)) {
            return Optional.empty();
        }

        // Attack and defence are normalized against the same competition's venue-specific
        // scoring baseline, preventing one league's scoring environment from leaking into another.
        double expectedHome = homeAttack.value() * awayDefence.value() / leagueHomeGoals.value();
        double expectedAway = awayAttack.value() * homeDefence.value() / leagueAwayGoals.value();

        return Optional.of(new ExpectedGoals(
                clampExpectedGoals(expectedHome),
                clampExpectedGoals(expectedAway),
                Math.min(Math.min(homeAttack.samples(), awayDefence.samples()), leagueHomeGoals.samples()),
                Math.min(Math.min(awayAttack.samples(), homeDefence.samples()), leagueAwayGoals.samples())));
    }

    public Optional<ExpectedGoals> estimateFromExpectedGoals(Match match) {
        if (!hasTeamsAndLeague(match)) {
            return Optional.empty();
        }

        Long leagueId = match.getSeason().getLeague().getId();
        TeamHistory home = loadTeamHistory(match.getHomeTeam(), leagueId);
        TeamHistory away = loadTeamHistory(match.getAwayTeam(), leagueId);
        List<Match> leagueMatches = loadLeagueHistory(leagueId);

        Rate homeAttack = chooseTeamRate(home, true, Metric.XG_FOR);
        Rate homeDefence = chooseTeamRate(home, true, Metric.XG_AGAINST);
        Rate awayAttack = chooseTeamRate(away, false, Metric.XG_FOR);
        Rate awayDefence = chooseTeamRate(away, false, Metric.XG_AGAINST);

        Rate leagueHomeXg = calculateLeagueAverage(leagueMatches, true, true);
        Rate leagueAwayXg = calculateLeagueAverage(leagueMatches, false, true);

        if (homeAttack == null || homeDefence == null || awayAttack == null || awayDefence == null
                || !validLeagueBaseline(leagueHomeXg) || !validLeagueBaseline(leagueAwayXg)) {
            return Optional.empty();
        }

        double expectedHome = homeAttack.value() * awayDefence.value() / leagueHomeXg.value();
        double expectedAway = awayAttack.value() * homeDefence.value() / leagueAwayXg.value();

        return Optional.of(new ExpectedGoals(
                clampExpectedGoals(expectedHome),
                clampExpectedGoals(expectedAway),
                Math.min(Math.min(homeAttack.samples(), awayDefence.samples()), leagueHomeXg.samples()),
                Math.min(Math.min(awayAttack.samples(), homeDefence.samples()), leagueAwayXg.samples())));
    }

    private TeamHistory loadTeamHistory(Team team, Long leagueId) {
        List<Match> matches = matchRepository.findRecentFinishedMatchesForTeamInLeague(
                team.getId(), leagueId, MatchStatus.FINISHED, PageRequest.of(0, TEAM_HISTORY_SIZE));
        return new TeamHistory(team, matches == null ? List.of() : matches);
    }

    private List<Match> loadLeagueHistory(Long leagueId) {
        List<Match> matches = matchRepository.findRecentFinishedMatchesInLeague(
                leagueId, MatchStatus.FINISHED, PageRequest.of(0, LEAGUE_BASELINE_SIZE));
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
        List<Match> eligible = new ArrayList<>();
        for (Match match : history.matches()) {
            boolean teamWasHome = match.getHomeTeam().getId().equals(history.team().getId());
            if (homeVenue != null && teamWasHome != homeVenue) {
                continue;
            }
            Double value = metric.value(match, teamWasHome);
            if (isValidMetric(value)) {
                eligible.add(match);
            }
        }

        return weightedTeamRate(eligible, history.team(), metric);
    }

    private Rate weightedTeamRate(List<Match> matches, Team team, Metric metric) {
        if (matches.isEmpty()) {
            return new Rate(0, 0);
        }

        double weightedTotal = 0;
        double totalWeight = 0;
        int validSamples = 0;
        for (int i = 0; i < matches.size(); i++) {
            Match match = matches.get(i);
            boolean teamWasHome = match.getHomeTeam().getId().equals(team.getId());
            Double value = metric.value(match, teamWasHome);
            if (!isValidMetric(value)) {
                continue;
            }

            double weight = Math.pow(TEAM_RECENCY_DECAY, i);
            weightedTotal += value * weight;
            totalWeight += weight;
            validSamples++;
        }

        return totalWeight == 0 ? new Rate(0, 0) : new Rate(weightedTotal / totalWeight, validSamples);
    }

    private Rate calculateLeagueAverage(List<Match> matches, boolean homeSide, boolean xg) {
        double weightedTotal = 0;
        double totalWeight = 0;
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
            validSamples++;
        }

        return totalWeight == 0 ? new Rate(0, 0) : new Rate(weightedTotal / totalWeight, validSamples);
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

    private record Rate(double value, int samples) {
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
