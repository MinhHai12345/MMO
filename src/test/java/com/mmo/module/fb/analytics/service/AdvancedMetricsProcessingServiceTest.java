package com.mmo.module.fb.analytics.service;

import com.mmo.module.fb.domain.league.repository.SeasonRepository;
import com.mmo.module.fb.domain.match.repository.H2HRecordRepository;
import com.mmo.module.fb.domain.match.repository.MatchRepository;
import com.mmo.module.fb.domain.team.repository.TeamFormRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdvancedMetricsProcessingServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private SeasonRepository seasonRepository;

    @Mock
    private TeamFormRepository teamFormRepository;

    @Mock
    private H2HRecordRepository h2HRecordRepository;

    @InjectMocks
    private AdvancedMetricsProcessingService service;

    @Test
    void returnsCompletedReportWhenNoCurrentSeasonsOrHistoricalMatchesExist() {
        when(seasonRepository.findByIsCurrentTrueAndLeagueIsNotNullAndYearIsNotNull())
                .thenReturn(List.of());
        when(matchRepository.findFinishedScoredMatches(eq(
                com.mmo.module.fb.domain.match.enums.MatchStatus.FINISHED), any()))
                .thenReturn(List.of());
        when(h2HRecordRepository.findAllWithTeams()).thenReturn(List.of());

        var report = service.process();

        assertEquals("COMPLETED", report.status());
        assertEquals(0, report.teamFormsUpdated());
        assertEquals(0, report.h2hRecordsUpdated());
        assertTrue(report.completedAt().isAfter(report.startedAt()));
        verify(teamFormRepository, never()).saveAll(any());
        verify(h2HRecordRepository, never()).saveAll(any());
    }

    @Test
    void doesNotReadRepositoriesWhenStopIsAlreadyRequested() {
        var report = service.process(() -> true);

        assertEquals("INTERRUPTED", report.status());
        assertTrue(report.stopped());
        verify(seasonRepository, never()).findByIsCurrentTrueAndLeagueIsNotNullAndYearIsNotNull();
        verify(matchRepository, never()).findFinishedScoredMatches(any(), any());
        verify(h2HRecordRepository, never()).findAllWithTeams();
    }
}
