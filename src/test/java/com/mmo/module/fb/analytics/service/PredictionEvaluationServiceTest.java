package com.mmo.module.fb.analytics.service;

import com.mmo.module.fb.analytics.service.impl.PredictionEvaluationServiceImpl;
import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.entity.MatchPrediction;
import com.mmo.module.fb.domain.entity.PredictionPerformance;
import com.mmo.module.fb.domain.enums.MatchStatus;
import com.mmo.module.fb.domain.repository.MatchPredictionRepository;
import com.mmo.module.fb.domain.repository.PredictionPerformanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PredictionEvaluationServiceTest {

    private MatchPredictionRepository predictionRepository;
    private PredictionPerformanceRepository performanceRepository;
    private PredictionEvaluationService service;

    @BeforeEach
    void setUp() {
        predictionRepository = mock(MatchPredictionRepository.class);
        performanceRepository = mock(PredictionPerformanceRepository.class);
        service = new PredictionEvaluationServiceImpl(predictionRepository, performanceRepository);
    }

    @Test
    void evaluatesFinishedPredictionAndStoresBrierQualityScore() {
        Match match = Match.builder()
                .homeScore(2)
                .awayScore(1)
                .status(MatchStatus.FINISHED)
                .build();
        MatchPrediction prediction = MatchPrediction.builder()
                .match(match)
                .winHomeProb(new BigDecimal("0.60"))
                .drawProb(new BigDecimal("0.25"))
                .winAwayProb(new BigDecimal("0.15"))
                .predictedResult("HOME")
                .predictionStatus("PENDING")
                .build();
        when(predictionRepository.findUnevaluatedPredictions(
                eq(MatchStatus.FINISHED), any(Pageable.class))).thenReturn(List.of(prediction));

        int evaluated = service.evaluateFinishedPredictions();

        assertEquals(1, evaluated);
        assertEquals("HOME", prediction.getResult());
        assertEquals("EVALUATED", prediction.getPredictionStatus());

        var captor = org.mockito.ArgumentCaptor.forClass(PredictionPerformance.class);
        verify(performanceRepository).save(captor.capture());
        PredictionPerformance performance = captor.getValue();
        assertEquals("HOME", performance.getActualResult());
        assertEquals(Boolean.TRUE, performance.getIsCorrect());
        assertEquals(0, new BigDecimal("87.75").compareTo(performance.getAccuracyScore()));
        assertNotNull(performance.getEvaluatedAt());
        verify(predictionRepository).save(prediction);
    }

    @Test
    void recordsIncorrectOutcomeWithoutTreatingItAsMissingData() {
        Match match = Match.builder()
                .homeScore(0)
                .awayScore(1)
                .status(MatchStatus.FINISHED)
                .build();
        MatchPrediction prediction = MatchPrediction.builder()
                .match(match)
                .winHomeProb(new BigDecimal("0.55"))
                .drawProb(new BigDecimal("0.25"))
                .winAwayProb(new BigDecimal("0.20"))
                .predictedResult("HOME")
                .predictionStatus("PENDING")
                .build();
        when(predictionRepository.findUnevaluatedPredictions(
                eq(MatchStatus.FINISHED), any(Pageable.class))).thenReturn(List.of(prediction));

        service.evaluateFinishedPredictions();

        var captor = org.mockito.ArgumentCaptor.forClass(PredictionPerformance.class);
        verify(performanceRepository).save(captor.capture());
        assertEquals("AWAY", captor.getValue().getActualResult());
        assertEquals(Boolean.FALSE, captor.getValue().getIsCorrect());
    }
}
