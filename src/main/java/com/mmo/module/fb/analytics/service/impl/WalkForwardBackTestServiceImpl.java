package com.mmo.module.fb.analytics.service.impl;

import com.mmo.module.fb.analytics.service.WalkForwardBackTestService;
import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.entity.MatchPrediction;
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
import java.util.Objects;

/**
 * Evaluates only predictions recorded before kickoff.
 *
 * Calibration is walk-forward: each test prediction is calibrated only with earlier
 * predictions whose outcomes should have been available before the test kickoff.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalkForwardBackTestServiceImpl implements WalkForwardBackTestService {

    private static final int MAX_SAMPLES = 10_000;
    private static final int MIN_CALIBRATION_SAMPLES = 100;
    private static final int CALIBRATION_REFRESH_GROUPS = 50;
    private static final double EPSILON = 1.0e-12;
    // The schema has no final-whistle timestamp. A conservative lag reduces leakage from
    // matches that kicked off earlier but were still in progress when the next match started.
    private static final Duration RESULT_AVAILABILITY_LAG = Duration.ofHours(4);

    private final MatchPredictionRepository matchPredictionRepository;

    @Override
    @Transactional(readOnly = true)
    public BackTestReport run(Instant fromInclusive, Instant toExclusive) {
        if (fromInclusive == null || toExclusive == null || !fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException("fromInclusive must be before toExclusive");
        }

        // Keep a bounded recent training history, but never cap the requested test window.
        List<Sample> samples = new ArrayList<>();
        matchPredictionRepository.findWalkForwardTrainingCandidates(
                        fromInclusive, MatchStatus.FINISHED, PageRequest.of(0, MAX_SAMPLES))
                .stream()
                .map(this::toSample)
                .filter(Objects::nonNull)
                .forEach(samples::add);

        int page = 0;
        List<MatchPrediction> pageContent;
        do {
            pageContent = matchPredictionRepository.findWalkForwardTestCandidates(
                    fromInclusive, toExclusive, MatchStatus.FINISHED, PageRequest.of(page, MAX_SAMPLES));
            pageContent.stream()
                    .map(this::toSample)
                    .filter(Objects::nonNull)
                    .forEach(samples::add);
            page++;
        } while (pageContent.size() == MAX_SAMPLES);

        samples.sort(Comparator.comparing(Sample::kickoff));

        List<Sample> prior = new ArrayList<>();
        Metrics baselineMetrics = new Metrics();
        Metrics rawMetrics = new Metrics();
        Metrics calibratedMetrics = new Metrics();
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

            // Only train on outcomes that are at least four hours older than this kickoff.
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

                // Smoothed historical class-frequency baseline uses only matured prior outcomes.
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
                    tested++;
                }
                groupsSinceCalibration++;
            }

            index = groupEnd;
        }

        BackTestReport report = new BackTestReport(
                fromInclusive, toExclusive, tested, calibratedCount, prior.size(),
                baselineMetrics.snapshot(), rawMetrics.snapshot(), calibratedMetrics.snapshot(),
                tested < MIN_CALIBRATION_SAMPLES
                        ? "INSUFFICIENT_TEST_SAMPLES"
                        : calibratedCount == 0 ? "INSUFFICIENT_CALIBRATION_HISTORY" : "OK");
        log.info("Walk-forward backtest: from={}, to={}, matches={}, calibrated={}, calibrationHistory={}, baselineBrier={}, rawBrier={}, calibratedBrier={}, baselineLogLoss={}, rawLogLoss={}, calibratedLogLoss={}, baselineEce={}, rawEce={}, calibratedEce={}, status={}",
                fromInclusive, toExclusive, tested, calibratedCount, prior.size(),
                report.baseline().brierScore(), report.raw().brierScore(), report.calibrated().brierScore(),
                report.baseline().logLoss(), report.raw().logLoss(), report.calibrated().logLoss(),
                report.baseline().expectedCalibrationError(), report.raw().expectedCalibrationError(),
                report.calibrated().expectedCalibrationError(), report.status());
        return report;
    }

    /**
     * Laplace-smoothed historical outcome frequencies provide a simple benchmark.
     * The +1 prior per class prevents zero probabilities and uses no future outcomes.
     */
    private double[] baselineProbabilities(List<Sample> prior) {
        double[] counts = {1.0, 1.0, 1.0};
        for (Sample sample : prior) {
            counts[sample.actualClass()]++;
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

        int actualClass = match.getHomeScore() > match.getAwayScore() ? 0
                : match.getHomeScore().equals(match.getAwayScore()) ? 1 : 2;
        return new Sample(match.getMatchTime(), probabilities, actualClass);
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

    /**
     * Grid-search multiclass temperature scaling against historical log loss.
     * T > 1 softens probabilities; T < 1 sharpens them.
     */
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

    private record Sample(Instant kickoff, double[] probabilities, int actualClass) {
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

                int bin = Math.min(CALIBRATION_BINS - 1,
                        (int) (probabilities[i] * CALIBRATION_BINS));
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

        private double expectedCalibrationError() {
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
