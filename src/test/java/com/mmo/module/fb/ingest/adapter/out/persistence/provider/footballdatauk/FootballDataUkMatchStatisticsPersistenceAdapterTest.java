package com.mmo.module.fb.ingest.adapter.out.persistence.provider.footballdatauk;

import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.league.entity.League;
import com.mmo.module.fb.domain.league.entity.Season;
import com.mmo.module.fb.ingest.policy.DataSourcePolicy;
import com.mmo.module.fb.domain.match.entity.MatchAdvancedStats;
import com.mmo.module.fb.domain.match.repository.MatchAdvancedStatsRepository;
import com.mmo.module.fb.domain.match.repository.MatchRepository;
import com.mmo.module.fb.domain.match.repository.MatchRepository;
import com.mmo.module.fb.domain.match.service.MatchStatisticsSnapshotPersistenceService;
import com.mmo.module.fb.domain.team.entity.Team;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class FootballDataUkMatchStatisticsPersistenceAdapterTest {

    @Test
    void mapsAllPublishedTeamStatisticsWithoutConfusingFoulsAndFreeKicks() {
        MatchAdvancedStatsRepository repository = mock(MatchAdvancedStatsRepository.class);
        when(repository.findByMatchIdAndTeamId(anyLong(), anyLong())).thenReturn(Optional.empty());
        when(repository.save(any(MatchAdvancedStats.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Team home = new Team();
        home.setId(10L);
        Team away = new Team();
        away.setId(20L);
        Match match = new Match();
        match.setId(100L);
        match.setSeason(supportedSeason());
        match.setHomeTeam(home);
        match.setAwayTeam(away);

        FootballDataUkMatchStatisticsPersistenceAdapter mapper = new FootballDataUkMatchStatisticsPersistenceAdapter(
                repository, mock(MatchStatisticsSnapshotPersistenceService.class), new DataSourcePolicy(),
                new com.mmo.module.fb.ingest.adapter.out.persistence.core.SourceAwareMergeAdapter(new DataSourcePolicy(),
                        mock(MatchRepository.class),
                        mock(com.mmo.module.fb.domain.match.repository.MatchFieldProvenanceRepository.class)));
        Map<String, String> row = Map.ofEntries(
                Map.entry("FTHG", "2"), Map.entry("FTAG", "1"),
                Map.entry("HS", "14"), Map.entry("HST", "6"), Map.entry("HHW", "1"),
                Map.entry("HC", "7"), Map.entry("HF", "9"), Map.entry("HFKC", "13"),
                Map.entry("HO", "2"), Map.entry("HY", "3"), Map.entry("HR", "0"),
                Map.entry("HBP", "30"), Map.entry("HXG", "1.85"), Map.entry("AXG", "0.75")
        );

        assertEquals(2, mapper.persist(match, row));
        var captor = org.mockito.ArgumentCaptor.forClass(MatchAdvancedStats.class);
        verify(repository, times(2)).save(captor.capture());
        MatchAdvancedStats stats = captor.getAllValues().get(0);

        assertEquals(14, stats.getTotalShots());
        assertEquals(6, stats.getShotsOnTarget());
        assertEquals(1, stats.getHitWoodwork());
        assertEquals(7, stats.getCornerKicks());
        assertEquals(9, stats.getFouls());
        assertEquals(13, stats.getFreeKicksConceded());
        assertEquals(2, stats.getOffsides());
        assertEquals(3, stats.getYellowCards());
        assertEquals(0, stats.getRedCards());
        assertEquals(30, stats.getBookingPoints());
        assertEquals(1.85, stats.getXg().doubleValue(), 0.0001);
        assertEquals(0.75, stats.getXga().doubleValue(), 0.0001);
        assertNull(stats.getMissed(), "Goals conceded must not be mapped to missed chances");
    }

    private Season supportedSeason() {
        League league = new League();
        league.setId(39L);
        league.setName("Premier League");
        Season season = new Season();
        season.setId(2024L);
        season.setLeague(league);
        season.setYear("2024/2025");
        return season;
    }
}
