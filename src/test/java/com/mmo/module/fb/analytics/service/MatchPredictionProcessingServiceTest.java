package com.mmo.module.fb.analytics.service;

import com.mmo.module.fb.analytics.engine.CompositePredictionEngine;
import com.mmo.module.fb.analytics.repository.MatchPredictionRepository;
import com.mmo.module.fb.domain.match.repository.MatchRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchPredictionProcessingServiceTest {

    @Mock
    private MatchPredictionRepository matchPredictionRepository;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private CompositePredictionEngine compositePredictionEngine;

    @InjectMocks
    private MatchPredictionProcessingService service;

    @Test
    void returnsCompletedReportWhenNoUpcomingFixturesExist() {
        when(matchRepository.findPredictionCandidates(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(com.mmo.module.fb.domain.match.enums.MatchStatus.SCHEDULED),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.List.of());

        var report = service.processUpcomingMatches();

        assertEquals("COMPLETED", report.status());
        assertEquals(0, report.candidates());
        assertEquals(0, report.processed());
        assertEquals(0, report.failed());
        assertTrue(report.to().isAfter(report.from()));
        verify(matchPredictionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doesNotQueryFixturesWhenStopIsAlreadyRequested() {
        var report = service.processUpcomingMatches(() -> true);

        assertEquals("INTERRUPTED", report.status());
        assertTrue(report.stopped());
        verify(matchRepository, never()).findPredictionCandidates(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }
}
