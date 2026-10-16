package com.mmo.module.fb.analytics.service;

import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.entity.MatchPrediction;
import com.mmo.module.fb.domain.enums.MatchStatus;
import com.mmo.module.fb.domain.repository.MatchPredictionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Evaluates only predictions that were recorded before kickoff.
 *
 * Calibration is walk-forward: each test prediction is calibrated using only earlier
 * eligible predictions whose matches had already finished before that kickoff.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalkForwardBacktestService {

    private static final int MAX_SAMPLES = 10_000;
    private static final int MIN_CALIBRATION_SAMPLES = 100;
    private static final double EPSILON = 1.0e-12;

    private final MatchPredictionRepository matchPredictionRepository;

    @Transactional(readOnly = true)
    public BacktestReport run(Instant fromInclusive, Instant toExclusive) {
        if (fromInclusive == null || toExclusive == null || !fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException("fromInclusive must be before toExclusive");
        }

        List<Sample> samples = matchPredictionRepository.findWalkForwardCandidates(
                        toExclusive, MatchStatus.FINISHED, PageRequest.of(0, MAX_SAMPLES))
                .stream()
                .map(this::toSample)
                .filter(sample -> sample != null)
                .sorted(Comparator.comparing(Sample::kickoff))
                .toList();

        List<Sample> prior = new ArrayList<>();
        Metrics rawMetrics = new Metrics();
        Metrics calibratedMetrics = new Metrics();
        int tested = 0;
        int calibratedCount = 0;

        for (Sample sample : samples) {
            // Outcomes from matches kicking off at or after this kickoff are never used.
            if (!sample.kickoff().isBefore(toExclusive)) {
                continue;
            }
            if (sample.kickoff().isBefore(fromInclusive)) {
                prior.add(sample);
                continue;
            }

            double[] calibrated = sample.probabilities();
            if (prior.size() >= MIN_CALIBRATION_SAMPLES) {
                double temperature = fitTemperature(prior);
                calibrated = applyTemperature(sample.probabilities(), temperature);
                calibratedCount++;
            }

            rawMetrics.add(sample.probabilities(), sample.actualClass());
            calibratedMetrics.add(calibrated, sample.actualClass());
            tested++;
            prior.add(sample);
        }

        BacktestReport report = new BacktestReport(
                fromInclusive, toExclusive, tested, calibratedCount, prior.size(),
                rawMetrics.snapshot(), calibratedMetrics.snapshot(),
                tested < MIN_CALIBRATION_SAMPLES
                        ? "INSUFFICIENT_TEST_SAMPLES"
                        : calibratedCount == 0 ? "INSUFFICIENT_CALIBRATION_HISTORY" : "OK");
        log.info("Walk-forward backtest: from={}, to={}, matches={}, calibrated={}, rawBrier={}, calibratedBrier={}, rawLogLoss={}, calibratedLogLoss={}, status={}",
                fromInclusive, toExclusive, tested, calibratedCount,
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

    private double value(java.math.BigDecimal value) {
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
            double temperature = step / 10.0;
            double totalLoss = 0.0;
            for (Sample sample : training) {
                double[] scaled = applyTemperature(sample.probabilities(), temperature);
                totalLoss -= Math.log(Math.max(EPSILON, scaled[sample.actualClass()]));
            }
            double averageLoss = totalLoss / training.size();
            if (averageLoss < bestLoss) {
                bestLoss = averageLoss;
                bestTemperature = temperature;
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

    public record MetricSnapshot(double brierScore, double logLoss, double accuracy) {
    }

    public record BacktestReport(
            Instant from,
            Instant to,
            int testedMatches,
            int calibratedMatches,
            int eligibleHistoricalMatches,
            MetricSnapshot raw,
            MetricSnapshot calibrated,
            String status) {
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
