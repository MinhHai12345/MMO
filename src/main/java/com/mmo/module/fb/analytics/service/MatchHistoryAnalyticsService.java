package com.mmo.module.fb.analytics.service;

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
public class MatchHistoryAnalyticsService {

    private static final int HISTORY_SIZE = 20;
    private static final int MIN_VENUE_SAMPLES = 3;
    private static final int MIN_OVERALL_SAMPLES = 5;
    private static final double RECENCY_DECAY = 0.92;
    private static final double MIN_EXPECTED_GOALS = 0.15;
    private static final double MAX_EXPECTED_GOALS = 4.50;

    private final MatchRepository matchRepository;

    public Optional<ExpectedGoals> estimateFromGoals(Match match) {
        if (!hasTeams(match)) {
            return Optional.empty();
        }

        Long leagueId = match.getSeason().getLeague().getId();
        TeamHistory home = load(match.getHomeTeam(), leagueId);
        TeamHistory away = load(match.getAwayTeam(), leagueId);

        Rate homeAttack = chooseRate(home, true, Metric.GOALS_FOR);
        Rate homeDefence = chooseRate(home, true, Metric.GOALS_AGAINST);
        Rate awayAttack = chooseRate(away, false, Metric.GOALS_FOR);
        Rate awayDefence = chooseRate(away, false, Metric.GOALS_AGAINST);

        // When venue-specific history is sparse, chooseRate falls back to all recent
        // matches only when there are enough observations; it never invents a rate.
        if (homeAttack == null || homeDefence == null || awayAttack == null || awayDefence == null) {
            return Optional.empty();
        }

        return Optional.of(new ExpectedGoals(
                combineAttackAndDefence(homeAttack.value(), awayDefence.value()),
                combineAttackAndDefence(awayAttack.value(), homeDefence.value()),
                homeAttack.samples() + awayDefence.samples(),
                awayAttack.samples() + homeDefence.samples()));
    }

    public Optional<ExpectedGoals> estimateFromExpectedGoals(Match match) {
        if (!hasTeams(match)) {
            return Optional.empty();
        }

        TeamHistory home = load(match.getHomeTeam());
        TeamHistory away = load(match.getAwayTeam());

        Rate homeAttack = chooseRate(home, true, Metric.XG_FOR);
        Rate homeDefence = chooseRate(home, true, Metric.XG_AGAINST);
        Rate awayAttack = chooseRate(away, false, Metric.XG_FOR);
        Rate awayDefence = chooseRate(away, false, Metric.XG_AGAINST);

        if (homeAttack == null || homeDefence == null || awayAttack == null || awayDefence == null) {
            return Optional.empty();
        }

        return Optional.of(new ExpectedGoals(
                combineAttackAndDefence(homeAttack.value(), awayDefence.value()),
                combineAttackAndDefence(awayAttack.value(), homeDefence.value()),
                homeAttack.samples() + awayDefence.samples(),
                awayAttack.samples() + homeDefence.samples()));
    }

    private TeamHistory load(Team team, Long leagueId) {
        List<Match> matches = matchRepository.findRecentFinishedMatchesForTeamInLeague(
                team.getId(), leagueId, MatchStatus.FINISHED, PageRequest.of(0, HISTORY_SIZE));
        return new TeamHistory(team, matches == null ? List.of() : matches);
    }

    private Rate chooseRate(TeamHistory history, boolean homeVenue, Metric metric) {
        Rate venueRate = calculateRate(history, homeVenue, metric);
        if (venueRate.samples() >= MIN_VENUE_SAMPLES) {
            return venueRate;
        }

        Rate overallRate = calculateRate(history, null, metric);
        return overallRate.samples() >= MIN_OVERALL_SAMPLES ? overallRate : null;
    }

    private Rate calculateRate(TeamHistory history, Boolean homeVenue, Metric metric) {
        List<Match> eligible = new ArrayList<>();
        for (Match match : history.matches()) {
            boolean isHome = match.getHomeTeam().getId().equals(history.team().getId());
            if (homeVenue != null && isHome != homeVenue) {
                continue;
            }
            if (metric.isExpectedGoals() && metric.value(match, isHome) == null) {
                continue;
            }
            eligible.add(match);
        }

        if (eligible.isEmpty()) {
            return new Rate(0, 0);
        }

        double weightedTotal = 0;
        double totalWeight = 0;
        for (int i = 0; i < eligible.size(); i++) {
            Match match = eligible.get(i);
            boolean isHome = match.getHomeTeam().getId().equals(history.team().getId());
            Double value = metric.value(match, isHome);
            if (value == null || !Double.isFinite(value) || value < 0) {
                continue;
            }

            double weight = Math.pow(RECENCY_DECAY, i);
            weightedTotal += value * weight;
            totalWeight += weight;
        }

        return totalWeight == 0
                ? new Rate(0, 0)
                : new Rate(weightedTotal / totalWeight, eligible.size());
    }

    private double combineAttackAndDefence(double attackingRate, double opponentConcedingRate) {
        double combined = Math.sqrt(Math.max(0, attackingRate) * Math.max(0, opponentConcedingRate));
        return Math.max(MIN_EXPECTED_GOALS, Math.min(MAX_EXPECTED_GOALS, combined));
    }

    private boolean hasTeams(Match match) {
        return match != null && match.getHomeTeam() != null && match.getAwayTeam() != null
                && match.getHomeTeam().getId() != null && match.getAwayTeam().getId() != null
                && match.getSeason() != null && match.getSeason().getLeague() != null
                && match.getSeason().getLeague().getId() != null
                && !match.getHomeTeam().getId().equals(match.getAwayTeam().getId());
    }

    public record ExpectedGoals(double home, double away, int homeSamples, int awaySamples) {
    }

    private record TeamHistory(Team team, List<Match> matches) {
    }

    private record Rate(double value, int samples) {
    }

    private enum Metric {
        GOALS_FOR(false) {
            @Override Double value(Match match, boolean isHome) {
                Integer value = isHome ? match.getHomeScore() : match.getAwayScore();
                return value == null ? null : value.doubleValue();
            }
        },
        GOALS_AGAINST(false) {
            @Override Double value(Match match, boolean isHome) {
                Integer value = isHome ? match.getAwayScore() : match.getHomeScore();
                return value == null ? null : value.doubleValue();
            }
        },
        XG_FOR(true) {
            @Override Double value(Match match, boolean isHome) {
                return isHome ? match.getHomeXG() : match.getAwayXG();
            }
        },
        XG_AGAINST(true) {
            @Override Double value(Match match, boolean isHome) {
                return isHome ? match.getAwayXG() : match.getHomeXG();
            }
        };

        private final boolean expectedGoals;

        Metric(boolean expectedGoals) {
            this.expectedGoals = expectedGoals;
        }

        boolean isExpectedGoals() {
            return expectedGoals;
        }

        abstract Double value(Match match, boolean isHome);
    }
}
