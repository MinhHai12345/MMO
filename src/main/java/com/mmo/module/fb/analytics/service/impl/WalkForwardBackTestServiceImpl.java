package com.mmo.module.fb.analytics.service.impl;

import com.mmo.module.fb.analytics.service.WalkForwardBackTestService;
import com.mmo.module.fb.analytics.service.WalkForwardBackTestService.GroupMetricSnapshot;
import com.mmo.module.fb.analytics.service.WalkForwardBackTestService.MetricSnapshot;
import com.mmo.module.fb.domain.entity.League;
import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.entity.MatchPrediction;
import com.mmo.module.fb.domain.entity.Season;
import com.mmo.module.fb.domain.enums.MatchStatus;
import com.mmo.module.fb.domain.repository.MatchPredictionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalkForwardBackTestServiceImpl implements WalkForwardBackTestService {

    private static final int MAX_SAMPLES = 10_000;
    private static final int MIN_CALIBRATION_SAMPLES = 100;
    private static final int CALIBRATION_REFRESH_GROUPS = 50;
    private static final double EPSILON = 1.0e-12;
    // The schema has no final-whistle timestamp. This lag is only a conservative proxy.
    private static final Duration RESULT_AVAILABILITY_LAG = Duration.ofHours(4);
    private static final String UNKNOWN = "UNKNOWN";

    private final MatchPredictionRepository matchPredictionRepository;

    @Override
    @Transactional(readOnly = true)
    public BackTestReport run(Instant fromInclusive, Instant toExclusive) {
        if (fromInclusive == null || toExclusive == null || !fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException("fromInclusive must be before toExclusive");
        }

        List<Sample> samples = new ArrayList<>();
        matchPredictionRepository.findWalkForwardTrainingCandidates(
                        fromInclusive, MatchStatus.FINISHED, PageRequest.of(0, MAX_SAMPLES))
                .stream().map(this::toSample).filter(Objects::nonNull).forEach(samples::add);

        int page = 0;
        List<MatchPrediction> pageContent;
        do {
            pageContent = matchPredictionRepository.findWalkForwardTestCandidates(
                    fromInclusive, toExclusive, MatchStatus.FINISHED, PageRequest.of(page, MAX_SAMPLES));
            pageContent.stream().map(this::toSample).filter(Objects::nonNull).forEach(samples::add);
            page++;
        } while (pageContent.size() == MAX_SAMPLES);

        samples.sort(Comparator.comparing(Sample::kickoff));

        List<Sample> prior = new ArrayList<>();
        Metrics baselineMetrics = new Metrics();
        Metrics rawMetrics = new Metrics();
        Metrics calibratedMetrics = new Metrics();
        Map<GroupKey, GroupMetrics> groupedMetrics = new TreeMap<>(
                Comparator.comparing(GroupKey::league).thenComparing(GroupKey::season));
        int tested = 0;
        int calibratedCount = 0;
        int nextTrainingIndex = 0;
        int index = 0;
        int groupsSinceCalibration = CALIBRATION_REFRESH_GROUPS;
        double temperature = 1.0;

        while (index < samples.size()) {
            Instant kickoff = samples.get(index).kickoff();
            int groupEnd = index + 1;
            while (groupEnd < samples.size() && samples.get(groupEnd).kickoff().equals(kickoff)) {
                groupEnd++;
            }

            Instant trainingCutoff = kickoff.minus(RESULT_AVAILABILITY_LAG);
            while (nextTrainingIndex < index
                    && samples.get(nextTrainingIndex).kickoff().isBefore(trainingCutoff)) {
                prior.add(samples.get(nextTrainingIndex));
                nextTrainingIndex++;
            }

            if (!kickoff.isBefore(fromInclusive)) {
                if (prior.size() >= MIN_CALIBRATION_SAMPLES
                        && groupsSinceCalibration >= CALIBRATION_REFRESH_GROUPS) {
                    temperature = fitTemperature(prior);
                    groupsSinceCalibration = 0;
                }

                // All predictions at the same kickoff use the same pre-kickoff historical prior.
                double[] baselineProbabilities = baselineProbabilities(prior);
                for (int i = index; i < groupEnd; i++) {
                    Sample sample = samples.get(i);
                    double[] calibrated = sample.probabilities();
                    if (prior.size() >= MIN_CALIBRATION_SAMPLES) {
                        calibrated = applyTemperature(sample.probabilities(), temperature);
                        calibratedCount++;
                    }

                    baselineMetrics.add(baselineProbabilities, sample.actualClass());
                    rawMetrics.add(sample.probabilities(), sample.actualClass());
                    calibratedMetrics.add(calibrated, sample.actualClass());

                    GroupMetrics group = groupedMetrics.computeIfAbsent(
                            new GroupKey(sample.league(), sample.season()), ignored -> new GroupMetrics());
                    // Group-level baseline uses only matured outcomes from this league,
                    // pooled across seasons to avoid a sparse per-season prior.
                    double[] leagueBaseline = baselineProbabilities(prior, sample.league());
                    group.baseline().add(leagueBaseline, sample.actualClass());
                    group.raw().add(sample.probabilities(), sample.actualClass());
                    group.calibrated().add(calibrated, sample.actualClass());
                    tested++;
                }
                groupsSinceCalibration++;
            }
            index = groupEnd;
        }

        List<GroupMetricSnapshot> breakdown = groupedMetrics.entrySet().stream()
                .map(entry -> new GroupMetricSnapshot(
                        entry.getKey().league(),
                        entry.getKey().season(),
                        entry.getValue().raw().count(),
                        entry.getValue().baseline().snapshot(),
                        entry.getValue().raw().snapshot(),
                        entry.getValue().calibrated().snapshot()))
                .toList();

        BackTestReport report = new BackTestReport(
                fromInclusive, toExclusive, tested, calibratedCount, prior.size(),
                baselineMetrics.snapshot(), rawMetrics.snapshot(), calibratedMetrics.snapshot(),
                breakdown,
                tested < MIN_CALIBRATION_SAMPLES
                        ? "INSUFFICIENT_TEST_SAMPLES"
                        : calibratedCount == 0 ? "INSUFFICIENT_CALIBRATION_HISTORY" : "OK");

        log.info("Walk-forward backtest: from={}, to={}, matches={}, calibrated={}, calibrationHistory={}, " +
                        "baselineBrier={}, rawBrier={}, calibratedBrier={}, groups={}, status={}",
                fromInclusive, toExclusive, tested, calibratedCount, prior.size(),
                report.baseline().brierScore(), report.raw().brierScore(),
                report.calibrated().brierScore(), report.breakdown().size(), report.status());
        report.breakdown().forEach(group -> log.info(
                "Walk-forward group: league={}, season={}, matches={}, baselineBrier={}, rawBrier={}, " +
                        "calibratedBrier={}, rawLogLoss={}, calibratedLogLoss={}, rawAccuracy={}, rawEce={}",
                group.league(), group.season(), group.testedMatches(),
                group.baseline().brierScore(), group.raw().brierScore(), group.calibrated().brierScore(),
                group.raw().logLoss(), group.calibrated().logLoss(),
                group.raw().accuracy(), group.raw().expectedCalibrationError()));
        return report;
    }

    private double[] baselineProbabilities(List<Sample> prior) {
        return baselineProbabilities(prior, null);
    }

    private double[] baselineProbabilities(List<Sample> prior, String league) {
        double[] counts = {1.0, 1.0, 1.0};
        for (Sample sample : prior) {
            if (league == null || league.equals(sample.league())) {
                counts[sample.actualClass()]++;
            }
        }
        double total = counts[0] + counts[1] + counts[2];
        for (int i = 0; i < counts.length; i++) {
            counts[i] /= total;
        }
        return counts;
    }

    private Sample toSample(MatchPrediction prediction) {
        Match match = prediction.getMatch();
        if (match == null || match.getMatchTime() == null || match.getHomeScore() == null
                || match.getAwayScore() == null || prediction.getCalculatedAt() == null
                || !prediction.getCalculatedAt().isBefore(match.getMatchTime())) {
            return null;
        }

        double[] probabilities = normalize(
                value(prediction.getWinHomeProb()),
                value(prediction.getDrawProb()),
                value(prediction.getWinAwayProb()));
        if (probabilities == null) {
            return null;
        }

        Season seasonEntity = match.getSeason();
        League leagueEntity = seasonEntity == null ? null : seasonEntity.getLeague();
        String league = leagueEntity == null || leagueEntity.getName() == null
                ? UNKNOWN : leagueEntity.getName();
        String season = seasonEntity == null || seasonEntity.getYear() == null
                ? UNKNOWN : seasonEntity.getYear();

        int actualClass = match.getHomeScore() > match.getAwayScore() ? 0
                : match.getHomeScore().equals(match.getAwayScore()) ? 1 : 2;
        return new Sample(match.getMatchTime(), probabilities, actualClass, league, season);
    }

    private double value(BigDecimal value) {
        return value == null ? Double.NaN : value.doubleValue();
    }

    private double[] normalize(double home, double draw, double away) {
        if (!validProbability(home) || !validProbability(draw) || !validProbability(away)) {
            return null;
        }
        double total = home + draw + away;
        if (!Double.isFinite(total) || total <= 0) {
            return null;
        }
        return new double[]{home / total, draw / total, away / total};
    }

    private boolean validProbability(double value) {
        return Double.isFinite(value) && value >= 0.0 && value <= 1.0;
    }

    private double fitTemperature(List<Sample> training) {
        double bestTemperature = 1.0;
        double bestLoss = Double.POSITIVE_INFINITY;
        for (int step = 5; step <= 30; step++) {
            double candidateTemperature = step / 10.0;
            double totalLoss = 0.0;
            for (Sample sample : training) {
                double[] scaled = applyTemperature(sample.probabilities(), candidateTemperature);
                totalLoss -= Math.log(Math.max(EPSILON, scaled[sample.actualClass()]));
            }
            double averageLoss = totalLoss / training.size();
            if (averageLoss < bestLoss) {
                bestLoss = averageLoss;
                bestTemperature = candidateTemperature;
            }
        }
        return bestTemperature;
    }

    private double[] applyTemperature(double[] probabilities, double temperature) {
        double[] scaled = new double[3];
        double total = 0.0;
        for (int i = 0; i < probabilities.length; i++) {
            scaled[i] = Math.pow(Math.max(EPSILON, probabilities[i]), 1.0 / temperature);
            total += scaled[i];
        }
        for (int i = 0; i < scaled.length; i++) {
            scaled[i] /= total;
        }
        return scaled;
    }

    private record Sample(Instant kickoff, double[] probabilities, int actualClass, String league, String season) {
    }

    private record GroupKey(String league, String season) {
    }

    private record GroupMetrics(Metrics baseline, Metrics raw, Metrics calibrated) {
        private GroupMetrics() {
            this(new Metrics(), new Metrics(), new Metrics());
        }
    }

    private static final class Metrics {
        private double brier;
        private double logLoss;
        private int correct;
        private int count;
        private static final int CALIBRATION_BINS = 10;
        private final int[][] binCounts = new int[3][CALIBRATION_BINS];
        private final double[][] binProbabilitySums = new double[3][CALIBRATION_BINS];
        private final int[][] binOutcomeSums = new int[3][CALIBRATION_BINS];

        private void add(double[] probabilities, int actualClass) {
            for (int i = 0; i < probabilities.length; i++) {
                double outcome = i == actualClass ? 1.0 : 0.0;
                double error = probabilities[i] - outcome;
                brier += error * error;
                int bin = Math.min(CALIBRATION_BINS - 1, (int) (probabilities[i] * CALIBRATION_BINS));
                binCounts[i][bin]++;
                binProbabilitySums[i][bin] += probabilities[i];
                binOutcomeSums[i][bin] += (int) outcome;
            }
            logLoss -= Math.log(Math.max(EPSILON, probabilities[actualClass]));
            int predicted = 0;
            for (int i = 1; i < probabilities.length; i++) {
                if (probabilities[i] > probabilities[predicted]) {
                    predicted = i;
                }
            }
            if (predicted == actualClass) {
                correct++;
            }
            count++;
        }

        private int count() {
            return count;
        }

        private double expectedCalibrationError() {
            if (count == 0) {
                return Double.NaN;
            }
            double weightedAbsoluteError = 0.0;
            for (int outcomeClass = 0; outcomeClass < 3; outcomeClass++) {
                for (int bin = 0; bin < CALIBRATION_BINS; bin++) {
                    int binCount = binCounts[outcomeClass][bin];
                    if (binCount == 0) {
                        continue;
                    }
                    double meanPredicted = binProbabilitySums[outcomeClass][bin] / binCount;
                    double observedFrequency = (double) binOutcomeSums[outcomeClass][bin] / binCount;
                    weightedAbsoluteError += ((double) binCount / (3.0 * count))
                            * Math.abs(meanPredicted - observedFrequency);
                }
            }
            return weightedAbsoluteError;
        }

        private MetricSnapshot snapshot() {
            if (count == 0) {
                return new MetricSnapshot(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
            }
            return new MetricSnapshot(brier / count, logLoss / count, (double) correct / count,
                    expectedCalibrationError());
        }
    }
}
