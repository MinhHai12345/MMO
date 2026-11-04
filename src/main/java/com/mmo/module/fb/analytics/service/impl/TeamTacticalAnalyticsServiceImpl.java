package com.mmo.module.fb.analytics.service.impl;

import com.mmo.module.fb.analytics.service.TeamTacticalAnalyticsService;
import com.mmo.module.fb.analytics.service.TeamTacticalAnalyticsService.*;
import com.mmo.module.fb.match.entity.Match;
import com.mmo.module.fb.match.entity.MatchAdvancedStats;
import com.mmo.module.fb.match.enums.MatchStatus;
import com.mmo.module.fb.match.repository.MatchAdvancedStatsRepository;
import com.mmo.module.fb.team.entity.Team;
import com.mmo.module.fb.team.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamTacticalAnalyticsServiceImpl implements TeamTacticalAnalyticsService {

    private static final int DEFAULT_HISTORY_SIZE = 10;
    private static final int MAX_HISTORY_SIZE = 30;
    private static final int MIN_RECOMMENDED_SAMPLES = 5;
    private static final int SCALE = 3;
    private static final BigDecimal TREND_EPSILON = new BigDecimal("0.050");

    private final TeamRepository teamRepository;
    private final MatchAdvancedStatsRepository advancedStatsRepository;

    @Override
    public TeamTacticalProfile analyze(Long teamId, Long leagueId, int historySize) {
        if (teamId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "teamId is required");
        }

        int boundedSize = historySize <= 0
                ? DEFAULT_HISTORY_SIZE : Math.min(historySize, MAX_HISTORY_SIZE);

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Team not found: " + teamId));

        List<MatchAdvancedStats> rows = advancedStatsRepository.findRecentFinishedTeamStats(
                teamId, leagueId, MatchStatus.FINISHED, PageRequest.of(0, boundedSize));
        if (rows == null) rows = List.of();

        MetricAverage possession = average(rows, MatchAdvancedStats::getPossession);
        MetricAverage ppda = average(rows, this::ppda);
        MetricAverage fieldTilt = average(rows, MatchAdvancedStats::getFieldTilt);
        MetricAverage xgFor = average(rows, MatchAdvancedStats::getXg);
        MetricAverage xgAgainst = average(rows, MatchAdvancedStats::getXga);
        MetricAverage npxgFor = average(rows, MatchAdvancedStats::getNpxg);
        MetricAverage npxgAgainst = average(rows, MatchAdvancedStats::getNpxga);
        MetricAverage xgChain = average(rows, MatchAdvancedStats::getXgChain);
        MetricAverage xgBuildup = average(rows, MatchAdvancedStats::getXgBuildup);
        MetricAverage setPieceXg = average(rows, MatchAdvancedStats::getSetPieceXg);
        MetricAverage counterAttackXg = average(rows, MatchAdvancedStats::getCounterAttackXg);
        MetricAverage shots = averageInteger(rows, MatchAdvancedStats::getTotalShots);
        MetricAverage shotsOnTarget = averageInteger(rows, MatchAdvancedStats::getShotsOnTarget);
        MetricAverage shotsFromBox = averageInteger(rows, MatchAdvancedStats::getShotsFromBox);
        MetricAverage shotsOutsideBox = averageInteger(rows, MatchAdvancedStats::getShotsOutsideBox);
        MetricAverage deepCompletions = averageInteger(rows, MatchAdvancedStats::getDeepCompletions);
        MetricAverage finalThirdEntries = averageInteger(rows, MatchAdvancedStats::getFinalThirdEntries);
        MetricAverage passAccuracy = average(rows, MatchAdvancedStats::getPassAccuracy);

        List<String> warnings = buildWarnings(rows.size(), boundedSize, possession, ppda,
                fieldTilt, xgFor, xgAgainst, shots);
        List<String> observations = buildObservations(xgFor, xgAgainst, possession, ppda,
                fieldTilt, shots, shotsOnTarget, shotsFromBox, setPieceXg, counterAttackXg);

        String quality = rows.size() >= 8 && xgFor.samples() >= 5 && xgAgainst.samples() >= 5
                && (possession.samples() >= 5 || ppda.samples() >= 5)
                ? "GOOD"
                : rows.size() >= MIN_RECOMMENDED_SAMPLES ? "LIMITED" : "INSUFFICIENT";

        return new TeamTacticalProfile(
                team.getId(), team.getName(), leagueId, Instant.now(), boundedSize, rows.size(), quality,
                possession, ppda, fieldTilt, xgFor, xgAgainst, npxgFor, npxgAgainst,
                xgChain, xgBuildup, setPieceXg, counterAttackXg,
                shots, shotsOnTarget, shotsFromBox, shotsOutsideBox,
                deepCompletions, finalThirdEntries, passAccuracy,
                trend(rows, MatchAdvancedStats::getXg),
                trend(rows, MatchAdvancedStats::getXga),
                trendInteger(rows, MatchAdvancedStats::getTotalShots),
                trend(rows, MatchAdvancedStats::getPossession),
                trend(rows, this::ppda),
                rows.stream().map(row -> toSample(team, row)).toList(),
                observations, warnings);
    }

    private List<String> buildWarnings(int matches, int requested,
                                       MetricAverage possession, MetricAverage ppda,
                                       MetricAverage fieldTilt, MetricAverage xgFor,
                                       MetricAverage xgAgainst, MetricAverage shots) {
        List<String> warnings = new ArrayList<>();
        if (matches == 0) {
            warnings.add("NO_ADVANCED_HISTORY: no finished scored matches with advanced team statistics were found.");
            return warnings;
        }
        if (matches < MIN_RECOMMENDED_SAMPLES) {
            warnings.add("LIMITED_MATCH_SAMPLE: only " + matches
                    + " advanced-stat rows are available; at least five are recommended.");
        }
        if (matches < requested) {
            warnings.add("PARTIAL_HISTORY: requested " + requested + " matches but found " + matches + ".");
        }
        addCoverageWarning(warnings, "POSSESSION", possession, matches);
        addCoverageWarning(warnings, "PPDA", ppda, matches);
        addCoverageWarning(warnings, "FIELD_TILT", fieldTilt, matches);
        addCoverageWarning(warnings, "XG_FOR", xgFor, matches);
        addCoverageWarning(warnings, "XG_AGAINST", xgAgainst, matches);
        addCoverageWarning(warnings, "SHOTS", shots, matches);
        if (ppda.samples() > 0) {
            warnings.add("PPDA_NOTE: PPDA definitions and event coverage can differ by provider; compare only like-for-like provider data.");
        }
        return warnings;
    }

    private void addCoverageWarning(List<String> warnings, String metric,
                                    MetricAverage average, int matches) {
        if (average.samples() == 0) {
            warnings.add("NO_" + metric + ": metric is unavailable in the selected history.");
        } else if (average.samples() < matches) {
            warnings.add("PARTIAL_" + metric + "_COVERAGE: " + average.samples()
                    + " of " + matches + " matches contain this metric.");
        }
    }

    private List<String> buildObservations(MetricAverage xgFor, MetricAverage xgAgainst,
                                           MetricAverage possession, MetricAverage ppda,
                                           MetricAverage fieldTilt, MetricAverage shots,
                                           MetricAverage shotsOnTarget, MetricAverage shotsFromBox,
                                           MetricAverage setPieceXg, MetricAverage counterAttackXg) {
        List<String> observations = new ArrayList<>();
        if (xgFor.average() != null && xgAgainst.average() != null) {
            BigDecimal difference = bd(xgFor.average()).subtract(bd(xgAgainst.average()));
            observations.add("Average xG difference is " + difference.setScale(SCALE, RoundingMode.HALF_UP)
                    + " per match; this is a chance-quality indicator, not a result guarantee.");
        }
        if (possession.average() != null) {
            observations.add("Average recorded possession is " + bd(possession.average())
                    + "% across " + possession.samples() + " matches.");
        }
        if (fieldTilt.average() != null) {
            observations.add("Average field tilt is " + bd(fieldTilt.average())
                    + "% across " + fieldTilt.samples() + " matches; interpret using the source provider's definition.");
        }
        if (ppda.average() != null) {
            observations.add("Average PPDA is " + bd(ppda.average())
                    + "; lower values are commonly associated with more frequent defensive actions per opponent pass, subject to provider definition.");
        }
        if (shots.average() != null && shotsOnTarget.average() != null) {
            observations.add("Average shots on target / total shots: "
                    + bd(shotsOnTarget.average()) + " / " + bd(shots.average())
                    + " per match; this is volume and accuracy context, not chance quality.");
        }
        if (shots.average() != null && shotsFromBox.average() != null && shots.average() > 0) {
            double share = shotsFromBox.average() / shots.average() * 100.0;
            observations.add("Shots from inside the box average " + bd(share)
                    + "% of total shots, using separate metric averages.");
        }
        if (setPieceXg.average() != null || counterAttackXg.average() != null) {
            observations.add("Set-piece xG and counter-attack xG are reported separately; do not infer tactical intent from one metric alone.");
        }
        return observations;
    }

    private MatchTacticalSample toSample(Team team, MatchAdvancedStats row) {
        Match match = row.getMatch();
        boolean home = match.getHomeTeam() != null
                && team.getId().equals(match.getHomeTeam().getId());
        String opponent = home
                ? (match.getAwayTeam() == null ? null : match.getAwayTeam().getName())
                : (match.getHomeTeam() == null ? null : match.getHomeTeam().getName());
        return new MatchTacticalSample(
                match.getId(), match.getMatchTime(), opponent, home,
                home ? match.getHomeScore() : match.getAwayScore(),
                home ? match.getAwayScore() : match.getHomeScore(),
                value(row.getPossession()), value(ppda(row)), value(row.getFieldTilt()),
                value(row.getXg()), value(row.getXga()),
                row.getTotalShots(), row.getShotsOnTarget(), row.getShotsFromBox(),
                row.getFinalThirdEntries(), row.getDeepCompletions());
    }

    private MetricAverage average(List<MatchAdvancedStats> rows,
                                  Function<MatchAdvancedStats, BigDecimal> extractor) {
        double sum = 0;
        int samples = 0;
        for (MatchAdvancedStats row : rows) {
            BigDecimal value = extractor.apply(row);
            if (valid(value)) {
                sum += value.doubleValue();
                samples++;
            }
        }
        return new MetricAverage(samples == 0 ? null : round(sum / samples), samples);
    }

    private MetricAverage averageInteger(List<MatchAdvancedStats> rows,
                                         Function<MatchAdvancedStats, Integer> extractor) {
        double sum = 0;
        int samples = 0;
        for (MatchAdvancedStats row : rows) {
            Integer value = extractor.apply(row);
            if (value != null && value >= 0) {
                sum += value;
                samples++;
            }
        }
        return new MetricAverage(samples == 0 ? null : round(sum / samples), samples);
    }

    private MetricTrend trend(List<MatchAdvancedStats> rows,
                              Function<MatchAdvancedStats, BigDecimal> extractor) {
        int split = (rows.size() + 1) / 2;
        return trendFrom(rows, split, row -> {
            BigDecimal value = extractor.apply(row);
            return valid(value) ? value.doubleValue() : null;
        });
    }

    private MetricTrend trendInteger(List<MatchAdvancedStats> rows,
                                     Function<MatchAdvancedStats, Integer> extractor) {
        int split = (rows.size() + 1) / 2;
        return trendFrom(rows, split, row -> {
            Integer value = extractor.apply(row);
            return value == null || value < 0 ? null : value.doubleValue();
        });
    }

    /** Input rows are newest-first; the first half is the recent window. */
    private MetricTrend trendFrom(List<MatchAdvancedStats> rows, int split,
                                  Function<MatchAdvancedStats, Double> extractor) {
        if (split <= 0 || split >= rows.size()) {
            return new MetricTrend(null, null, null, "INSUFFICIENT_HISTORY", 0, 0);
        }
        MetricAverage recent = averageRange(rows, 0, split, extractor);
        MetricAverage previous = averageRange(rows, split, rows.size(), extractor);
        if (recent.average() == null || previous.average() == null) {
            return new MetricTrend(recent.average(), previous.average(), null,
                    "INSUFFICIENT_METRIC_COVERAGE", recent.samples(), previous.samples());
        }
        double change = recent.average() - previous.average();
        String direction = Math.abs(change) < TREND_EPSILON.doubleValue()
                ? "STABLE" : change > 0 ? "INCREASED" : "DECREASED";
        return new MetricTrend(recent.average(), previous.average(), round(change),
                direction, recent.samples(), previous.samples());
    }

    private MetricAverage averageRange(List<MatchAdvancedStats> rows, int from, int to,
                                       Function<MatchAdvancedStats, Double> extractor) {
        double sum = 0;
        int samples = 0;
        for (int i = from; i < to; i++) {
            Double value = extractor.apply(rows.get(i));
            if (value != null && Double.isFinite(value) && value >= 0) {
                sum += value;
                samples++;
            }
        }
        return new MetricAverage(samples == 0 ? null : round(sum / samples), samples);
    }

    private BigDecimal ppda(MatchAdvancedStats stats) {
        if (stats.getPpda() != null && stats.getPpda().signum() >= 0) return stats.getPpda();
        if (stats.getPpdaAtt() == null || stats.getPpdaDef() == null || stats.getPpdaDef() <= 0) return null;
        return BigDecimal.valueOf(stats.getPpdaAtt())
                .divide(BigDecimal.valueOf(stats.getPpdaDef()), SCALE, RoundingMode.HALF_UP);
    }

    private Double value(BigDecimal value) {
        return valid(value) ? value.doubleValue() : null;
    }

    private boolean valid(BigDecimal value) {
        return value != null && value.signum() >= 0;
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.HALF_UP).doubleValue();
    }

    private BigDecimal bd(Double value) {
        return BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.HALF_UP);
    }
}
