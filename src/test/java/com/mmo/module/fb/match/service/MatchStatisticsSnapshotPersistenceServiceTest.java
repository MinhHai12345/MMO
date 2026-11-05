package com.mmo.module.fb.match.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmo.module.fb.domain.match.enums.MatchProvider;
import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.match.entity.MatchStatisticsSourceSnapshot;
import com.mmo.module.fb.domain.match.repository.MatchStatisticsSourceSnapshotRepository;
import com.mmo.module.fb.domain.match.service.MatchStatisticsSnapshotPersistenceService;
import com.mmo.module.fb.domain.team.entity.Team;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MatchStatisticsSnapshotPersistenceServiceTest {

    @Test
    void appendsEveryFetchedPayloadPerMatchTeamAndProvider() {
        MatchStatisticsSourceSnapshotRepository repository =
                mock(MatchStatisticsSourceSnapshotRepository.class);
        when(repository.findByMatchIdAndTeamIdAndProvider(10L, 20L, MatchProvider.SOFA_SCORE))
                .thenReturn(Optional.empty());
        when(repository.save(any(MatchStatisticsSourceSnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Match match = new Match();
        match.setId(10L);
        Team team = new Team();
        team.setId(20L);
        Team away = new Team();
        away.setId(21L);
        match.setHomeTeam(team);
        match.setAwayTeam(away);

        MatchStatisticsSnapshotPersistenceService service =
                new MatchStatisticsSnapshotPersistenceService(repository, new ObjectMapper());
        service.upsert(match, team, MatchProvider.SOFA_SCORE, Map.of(
                "expectedGoals", "1.25", "totalShots", 14
        ));
        service.upsert(match, team, MatchProvider.SOFA_SCORE, Map.of(
                "expectedGoals", "1.50", "totalShots", 16
        ));

        var captor = org.mockito.ArgumentCaptor.forClass(MatchStatisticsSourceSnapshot.class);
        verify(repository, times(2)).save(captor.capture());
        MatchStatisticsSourceSnapshot saved = captor.getAllValues().get(1);
        assertEquals(match, saved.getMatch());
        assertEquals(team, saved.getTeam());
        assertEquals(MatchProvider.SOFA_SCORE, saved.getProvider());
        assertTrue(saved.getSnapshotJson().contains("\"expectedGoals\":\"1.25\""));
        assertNotNull(saved.getFetchedAt());
    }

    @Test
    void rejectsTeamThatDoesNotBelongToMatch() {
        MatchStatisticsSourceSnapshotRepository repository =
                mock(MatchStatisticsSourceSnapshotRepository.class);
        Match match = new Match();
        match.setId(10L);
        Team home = new Team();
        home.setId(20L);
        Team away = new Team();
        away.setId(21L);
        Team unrelated = new Team();
        unrelated.setId(22L);
        match.setHomeTeam(home);
        match.setAwayTeam(away);

        MatchStatisticsSnapshotPersistenceService service =
                new MatchStatisticsSnapshotPersistenceService(repository, new ObjectMapper());

        assertThrows(IllegalArgumentException.class,
                () -> service.upsert(match, unrelated, MatchProvider.SOFA_SCORE, Map.of("xg", 1)));
        verifyNoInteractions(repository);
    }

    @Test
    void ignoresIncompleteSourceIdentity() {
        MatchStatisticsSourceSnapshotRepository repository =
                mock(MatchStatisticsSourceSnapshotRepository.class);
        MatchStatisticsSnapshotPersistenceService service =
                new MatchStatisticsSnapshotPersistenceService(repository, new ObjectMapper());

        service.upsert(new Match(), new Team(), MatchProvider.SOFA_SCORE, Map.of("xg", 1));

        verifyNoInteractions(repository);
    }
}
