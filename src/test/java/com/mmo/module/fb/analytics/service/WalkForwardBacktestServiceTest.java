package com.mmo.module.fb.analytics.service;

import com.mmo.module.fb.analytics.service.impl.WalkForwardBackTestServiceImpl;
import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.entity.MatchPrediction;
import com.mmo.module.fb.domain.entity.League;
import com.mmo.module.fb.domain.entity.Season;
import com.mmo.module.fb.domain.enums.MatchStatus;
import com.mmo.module.fb.domain.repository.MatchPredictionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WalkForwardBacktestServiceTest {

    private MatchPredictionRepository repository;
    private WalkForwardBackTestService service;

    @BeforeEach
    void setUp() {
        repository = mock(MatchPredictionRepository.class);
        service = new WalkForwardBackTestServiceImpl(repository);
    }

    @Test
    void calibratesTestPredictionUsingOnlyEarlierFinishedMatches() {
        Instant from = Instant.parse("2025-06-01T00:00:00Z");
        Instant to = from.plus(2, ChronoUnit.DAYS);
        List<MatchPrediction> predictions = new ArrayList<>();

        for (int i = 1; i <= 100; i++) {
            Instant kickoff = from.minus(i, ChronoUnit.DAYS);
            predictions.add(prediction(kickoff, kickoff.minus(1, ChronoUnit.HOURS),
                    2, 0, "0.70", "0.20", "0.10"));
        }
        predictions.add(prediction(from.plus(1, ChronoUnit.HOURS),
                from.minus(1, ChronoUnit.HOURS), 2, 0, "0.55", "0.25", "0.20"));

        when(repository.findWalkForwardTrainingCandidates(eq(from), eq(MatchStatus.FINISHED), any(Pageable.class)))
                .thenReturn(predictions.subList(0, 100));
        when(repository.findWalkForwardTestCandidates(
                eq(from), eq(to), eq(MatchStatus.FINISHED), any(Pageable.class)))
                .thenReturn(List.of(predictions.get(100)));

        WalkForwardBackTestService.BackTestReport report = service.run(from, to);

        assertEquals(1, report.testedMatches());
        assertEquals(1, report.calibratedMatches());
        assertEquals(100, report.dixonColesRhoTrainingSamples());
        assertTrue(Double.isFinite(report.lastEstimatedDixonColesRho()));
        assertTrue(report.lastEstimatedDixonColesRho() >= -0.30);
        assertTrue(report.lastEstimatedDixonColesRho() <= 0.30);
        assertEquals("INSUFFICIENT_TEST_SAMPLES", report.status());
        assertEquals(1, report.breakdown().size());
        assertEquals("Premier League", report.breakdown().get(0).league());
        assertEquals("2025", report.breakdown().get(0).season());
        assertEquals(1, report.breakdown().get(0).testedMatches());
        assertTrue(Double.isFinite(report.breakdown().get(0).raw().brierScore()));
        assertTrue(Double.isFinite(report.baseline().brierScore()));
        assertTrue(Double.isFinite(report.baseline().logLoss()));
        assertTrue(Double.isFinite(report.raw().brierScore()));
        assertEquals(1, report.dixonColesMatches());
        assertEquals(report.raw().brierScore(), report.matchedRaw().brierScore(), 1.0e-12,
                "single eligible test match must produce identical raw and matched-cohort metrics");
        assertTrue(Double.isFinite(report.dixonColes().brierScore()));
        assertTrue(Double.isFinite(report.matchedBaseline().logLoss()));
        assertTrue(Double.isFinite(report.matchedRaw().brierScore()));
        assertTrue(Double.isFinite(report.matchedCalibrated().expectedCalibrationError()));
        assertEquals(1, report.breakdown().get(0).dixonColesMatches());
        assertTrue(Double.isFinite(report.breakdown().get(0).dixonColes().logLoss()));
        assertTrue(Double.isFinite(report.breakdown().get(0).matchedBaseline().brierScore()));
        assertTrue(Double.isFinite(report.breakdown().get(0).matchedRaw().logLoss()));
        assertTrue(Double.isFinite(report.breakdown().get(0).matchedCalibrated().brierScore()));
        assertEquals(100, report.breakdown().get(0).dixonColesRhoTrainingSamples());
        assertTrue(report.breakdown().get(0).leagueSpecificRho());
        assertTrue(Double.isFinite(report.breakdown().get(0).lastEstimatedDixonColesRho()));
        assertTrue(Double.isFinite(report.calibrated().brierScore()));
        assertTrue(Double.isFinite(report.raw().logLoss()));
        assertTrue(Double.isFinite(report.calibrated().logLoss()));
        assertTrue(Double.isFinite(report.raw().expectedCalibrationError()));
        assertTrue(Double.isFinite(report.calibrated().expectedCalibrationError()));
        assertTrue(report.raw().expectedCalibrationError() >= 0.0);
        assertTrue(report.raw().expectedCalibrationError() <= 1.0);
        assertTrue(report.calibrated().expectedCalibrationError() >= 0.0);
        assertTrue(report.calibrated().expectedCalibrationError() <= 1.0);
    }

    @Test
    void usesSmoothedUniformBaselineWhenNoMatureHistoryExists() {
        Instant from = Instant.parse("2025-06-01T00:00:00Z");
        Instant to = from.plus(1, ChronoUnit.DAYS);
        Instant kickoff = from.plus(1, ChronoUnit.HOURS);
        MatchPrediction sample = prediction(kickoff, from, 2, 0,
                "0.70", "0.20", "0.10");

        when(repository.findWalkForwardTrainingCandidates(eq(from), eq(MatchStatus.FINISHED), any(Pageable.class)))
                .thenReturn(List.of());
        when(repository.findWalkForwardTestCandidates(
                eq(from), eq(to), eq(MatchStatus.FINISHED), any(Pageable.class)))
                .thenReturn(List.of(sample));

        WalkForwardBackTestService.BackTestReport report = service.run(from, to);

        assertEquals(1, report.testedMatches());
        assertEquals(0.0, report.lastEstimatedDixonColesRho(), 1.0e-12);
        assertEquals(0, report.dixonColesRhoTrainingSamples());
        assertEquals(2.0 / 3.0, report.baseline().brierScore(), 1.0e-12);
        assertEquals(Math.log(3.0), report.baseline().logLoss(), 1.0e-12);
    }

    @Test
    void excludesPredictionCreatedAtOrAfterKickoff() {
        Instant from = Instant.parse("2025-06-01T00:00:00Z");
        Instant kickoff = from.plus(1, ChronoUnit.HOURS);
        MatchPrediction leaked = prediction(kickoff, kickoff.plus(1, ChronoUnit.MINUTES),
                1, 0, "0.90", "0.05", "0.05");
        Instant to = from.plus(1, ChronoUnit.DAYS);
        when(repository.findWalkForwardTrainingCandidates(eq(from), eq(MatchStatus.FINISHED), any(Pageable.class)))
                .thenReturn(List.of());
        when(repository.findWalkForwardTestCandidates(
                eq(from), eq(to), eq(MatchStatus.FINISHED), any(Pageable.class)))
                .thenReturn(List.of(leaked));

        WalkForwardBackTestService.BackTestReport report = service.run(from, to);

        assertEquals(0, report.testedMatches());
        assertEquals("INSUFFICIENT_TEST_SAMPLES", report.status());
    }

    @Test
    void pagesThroughEntireTestWindowBeyondTenThousandMatches() {
        Instant from = Instant.parse("2025-06-01T00:00:00Z");
        Instant to = from.plus(2, ChronoUnit.DAYS);
        Instant kickoff = from.plus(1, ChronoUnit.HOURS);
        MatchPrediction sample = prediction(kickoff, kickoff.minus(1, ChronoUnit.HOURS),
                2, 0, "0.55", "0.25", "0.20");
        List<MatchPrediction> firstPage = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            firstPage.add(sample);
        }

        when(repository.findWalkForwardTrainingCandidates(eq(from), eq(MatchStatus.FINISHED), any(Pageable.class)))
                .thenReturn(List.of());
        when(repository.findWalkForwardTestCandidates(
                eq(from), eq(to), eq(MatchStatus.FINISHED), any(Pageable.class)))
                .thenReturn(firstPage, List.of(sample));

        WalkForwardBackTestService.BackTestReport report = service.run(from, to);

        assertEquals(10_001, report.testedMatches());
        verify(repository, org.mockito.Mockito.times(2)).findWalkForwardTestCandidates(
                eq(from), eq(to), eq(MatchStatus.FINISHED), any(Pageable.class));
    }

    private MatchPrediction prediction(Instant kickoff, Instant calculatedAt,
                                       int homeScore, int awayScore,
                                       String homeProbability, String drawProbability, String awayProbability) {
        Match match = Match.builder()
                .matchTime(kickoff)
                .season(Season.builder()
                        .year("2025")
                        .league(League.builder().name("Premier League").build())
                        .build())
                .status(MatchStatus.FINISHED)
                .homeScore(homeScore)
                .awayScore(awayScore)
                .build();
        return MatchPrediction.builder()
                .match(match)
                .predictedHomeXg(new BigDecimal("1.60"))
                .predictedAwayXg(new BigDecimal("0.90"))
                .calculatedAt(calculatedAt)
                .winHomeProb(new BigDecimal(homeProbability))
                .drawProb(new BigDecimal(drawProbability))
                .winAwayProb(new BigDecimal(awayProbability))
                .predictionStatus("EVALUATED")
                .build();
    }
}
