package com.mmo.module.fb.analytics.service.impl;

import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.repository.MatchAdvancedStatsRepository;
import com.mmo.module.fb.domain.repository.MatchRepository;
import com.mmo.module.fb.domain.repository.PlayerMatchStatsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ModernFootballAnalyticsServiceImplTest {

    private final MatchRepository matches = mock(MatchRepository.class);
    private final MatchAdvancedStatsRepository advancedStats = mock(MatchAdvancedStatsRepository.class);
    private final PlayerMatchStatsRepository playerStats = mock(PlayerMatchStatsRepository.class);
    private final ModernFootballAnalyticsServiceImpl service =
            new ModernFootballAnalyticsServiceImpl(matches, advancedStats, playerStats);

    @Test
    void rejectsUnknownMatch() {
        when(matches.findById(404L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.analyze(404L));
    }

    @Test
    void returnsExplicitLimitationsWhenAdvancedDataIsMissing() {
        Match match = mock(Match.class);
        when(matches.findById(12L)).thenReturn(Optional.of(match));
        when(match.getId()).thenReturn(12L);
        when(match.getSeason()).thenReturn(null);
        when(match.getHomeTeam()).thenReturn(null);
        when(match.getAwayTeam()).thenReturn(null);
        when(match.getHomeScore()).thenReturn(null);
        when(match.getAwayScore()).thenReturn(null);
        when(match.getHomeXG()).thenReturn(null);
        when(match.getAwayXG()).thenReturn(null);
        when(match.getStatus()).thenReturn(com.mmo.module.fb.domain.enums.MatchStatus.SCHEDULED);
        when(advancedStats.findAllByMatchId(12L)).thenReturn(List.of());
        when(playerStats.findAllByMatchId(12L)).thenReturn(List.of());

        var report = service.analyze(12L);

        assertEquals(12L, report.matchId());
        assertNotNull(report.contentBrief());
        assertTrue(report.playerImpacts().isEmpty());
        assertTrue(report.dataLimitations().stream().anyMatch(v -> v.contains("Advanced team metrics")));
        assertTrue(report.dataLimitations().stream().anyMatch(v -> v.contains("Player match statistics")));
        assertTrue(report.dataLimitations().stream().anyMatch(v -> v.contains("Match xG")));
        assertTrue(report.contentBrief().editorialCaution().contains("Do not infer"));
    }
}
