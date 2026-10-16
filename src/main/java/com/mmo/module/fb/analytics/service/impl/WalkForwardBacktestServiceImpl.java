package com.mmo.module.fb.analytics.service.impl;

import com.mmo.module.fb.analytics.service.WalkForwardBacktestService;
import com.mmo.module.fb.analytics.service.WalkForwardBacktestService.BacktestReport;
import com.mmo.module.fb.analytics.service.WalkForwardBacktestService.MetricSnapshot;
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

/**
 * Evaluates only predictions recorded before kickoff.
 *
 * Calibration is walk-forward: each test prediction is calibrated only with earlier
 * predictions whose outcomes should have been available before the test kickoff.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalkForwardBacktestServiceImpl implements WalkForwardBacktestService {

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
    public BacktestReport run(Instant fromInclusive, Instant toExclusive) {
        if (fromInclusive == null || toExclusive == null || !fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException("fromInclusive must be before toExclusive");
        }

        // Keep a bounded recent training history, but never cap the requested test window.
        List<Sample> samples = new ArrayList<>();
        matchPredictionRepository.findWalkForwardTrainingCandidates(
                        fromInclusive, MatchStatus.FINISHED, PageRequest.of(0, MAX_SAMPLES))
                .stream()
                .map(this::toSample)
                .filter(sample -> sample != null)
                .forEach(samples::add);

        int page = 0;
        List<MatchPrediction> pageContent;
        do {
            pageContent = matchPredictionRepository.findWalkForwardTestCandidates(
                    fromInclusive, toExclusive, MatchStatus.FINISHED, PageRequest.of(page, MAX_SAMPLES));
            pageContent.stream()
                    .map(this::toSample)
                    .filter(sample -> sample != null)
                    .forEach(samples::add);
            page++;
        } while (pageContent.size() == MAX_SAMPLES);

        samples.sort(Comparator.comparing(Sample::kickoff));

        List<Sample> prior = new ArrayList<>();
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

                for (int i = index; i < groupEnd; i++) {
                    Sample sample = samples.get(i);
                    double[] calibrated = sample.probabilities();
                    if (prior.size() >= MIN_CALIBRATION_SAMPLES) {
                        calibrated = applyTemperature(sample.probabilities(), temperature);
                        calibratedCount++;
                    }

                    rawMetrics.add(sample.probabilities(), sample.actualClass());
                    calibratedMetrics.add(calibrated, sample.actualClass());
                    tested++;
                }
                groupsSinceCalibration++;
            }

            index = groupEnd;
        }

        BacktestReport report = new BacktestReport(
                fromInclusive, toExclusive, tested, calibratedCount, prior.size(),
                rawMetrics.snapshot(), calibratedMetrics.snapshot(),
                tested < MIN_CALIBRATION_SAMPLES
                        ? "INSUFFICIENT_TEST_SAMPLES"
                        : calibratedCount == 0 ? "INSUFFICIENT_CALIBRATION_HISTORY" : "OK");
        log.info("Walk-forward backtest: from={}, to={}, matches={}, calibrated={}, calibrationHistory={}, rawBrier={}, calibratedBrier={}, rawLogLoss={}, calibratedLogLoss={}, status={}",
                fromInclusive, toExclusive, tested, calibratedCount, prior.size(),
                report.raw().brierScore(), report.calibrated().brierScore(),
                report.raw().logLoss(), report.calibrated().logLoss(), report.status());
        return report;
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

        private void add(double[] probabilities, int actualClass) {
            for (int i = 0; i < probabilities.length; i++) {
                double error = probabilities[i] - (i == actualClass ? 1.0 : 0.0);
                brier += error * error;
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

        private MetricSnapshot snapshot() {
            if (count == 0) {
                return new MetricSnapshot(Double.NaN, Double.NaN, Double.NaN);
            }
            return new MetricSnapshot(brier / count, logLoss / count, (double) correct / count);
        }
    }
}
