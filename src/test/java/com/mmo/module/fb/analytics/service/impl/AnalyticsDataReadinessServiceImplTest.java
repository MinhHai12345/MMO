package com.mmo.module.fb.analytics.service.impl;

import com.mmo.module.fb.analytics.service.AnalyticsDataReadinessService.DataReadinessReport;
import com.mmo.module.fb.analytics.service.AnalyticsDataReadinessService.SeasonReadiness;
import com.mmo.module.fb.domain.enums.MatchStatus;
import com.mmo.module.fb.domain.repository.MatchRepository;
import com.mmo.module.fb.domain.repository.MatchRepository.SeasonDataReadinessProjection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsDataReadinessServiceImplTest {

    @Mock
    private MatchRepository matchRepository;

    @InjectMocks
    private AnalyticsDataReadinessServiceImpl service;

    @Test
    void reportsInsufficientHistoryAndZeroCoverageWithoutDivisionByZero() {
        SeasonDataReadinessProjection row = row(1L, "Premier League", "2026", 12L, 0L, 0L, 0L, 12L);
        when(matchRepository.findSeasonDataReadiness(MatchStatus.FINISHED)).thenReturn(List.of(row));

        DataReadinessReport report = service.getReadiness();

        assertEquals(1, report.seasons());
        assertEquals(12L, report.totalMatches());
        assertEquals(0L, report.finishedMatches());
        SeasonReadiness season = report.breakdown().get(0);
        assertEquals(0.0, season.scoreCoverage());
        assertEquals(0.0, season.expectedGoalsCoverage());
        assertEquals("INSUFFICIENT_DATA", season.goalsModelReadiness());
        assertEquals("INSUFFICIENT_DATA", season.expectedGoalsModelReadiness());
        assertEquals(2, season.issues().size());
    }

    @Test
    void marksModelsReadyAtMinimumRecommendedSampleCount() {
        SeasonDataReadinessProjection row = row(2L, "League", "2025", 30L, 20L, 10L, 10L, 10L);
        when(matchRepository.findSeasonDataReadiness(MatchStatus.FINISHED)).thenReturn(List.of(row));

        SeasonReadiness season = service.getReadiness().breakdown().get(0);

        assertEquals(0.5, season.scoreCoverage());
        assertEquals(0.5, season.expectedGoalsCoverage());
        assertEquals("READY", season.goalsModelReadiness());
        assertEquals("READY", season.expectedGoalsModelReadiness());
        assertTrue(season.issues().isEmpty());
    }

    @Test
    void returnsEmptyReportWhenNoSeasonsExist() {
        when(matchRepository.findSeasonDataReadiness(MatchStatus.FINISHED)).thenReturn(List.of());

        DataReadinessReport report = service.getReadiness();

        assertEquals(0, report.seasons());
        assertEquals(0L, report.totalMatches());
        assertTrue(report.breakdown().isEmpty());
    }

    private static SeasonDataReadinessProjection row(Long seasonId, String league, String year,
                                                      Long total, Long finished, Long scores,
                                                      Long xg, Long upcoming) {
        SeasonDataReadinessProjection row = mock(SeasonDataReadinessProjection.class);
        when(row.getSeasonId()).thenReturn(seasonId);
        when(row.getLeagueName()).thenReturn(league);
        when(row.getSeasonYear()).thenReturn(year);
        when(row.getTotalMatches()).thenReturn(total);
        when(row.getFinishedMatches()).thenReturn(finished);
        when(row.getFinishedWithScores()).thenReturn(scores);
        when(row.getFinishedWithExpectedGoals()).thenReturn(xg);
        when(row.getUpcomingMatches()).thenReturn(upcoming);
        return row;
    }
}
