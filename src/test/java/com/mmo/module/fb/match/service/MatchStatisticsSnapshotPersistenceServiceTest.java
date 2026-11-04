package com.mmo.module.fb.match.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.match.entity.Match;
import com.mmo.module.fb.match.entity.MatchStatisticsSourceSnapshot;
import com.mmo.module.fb.match.repository.MatchStatisticsSourceSnapshotRepository;
import com.mmo.module.fb.team.entity.Team;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MatchStatisticsSnapshotPersistenceServiceTest {

    @Test
    void upsertsLatestPayloadPerMatchTeamAndProvider() {
        MatchStatisticsSourceSnapshotRepository repository =
                mock(MatchStatisticsSourceSnapshotRepository.class);
        when(repository.findByMatchIdAndTeamIdAndProvider(10L, 20L, Provider.SOFA_SCORE))
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
        service.upsert(match, team, Provider.SOFA_SCORE, Map.of(
                "expectedGoals", "1.25",
                "totalShots", 14
        ));

        var captor = org.mockito.ArgumentCaptor.forClass(MatchStatisticsSourceSnapshot.class);
        verify(repository).save(captor.capture());
        MatchStatisticsSourceSnapshot saved = captor.getValue();
        assertEquals(match, saved.getMatch());
        assertEquals(team, saved.getTeam());
        assertEquals(Provider.SOFA_SCORE, saved.getProvider());
        assertTrue(saved.getSnapshotJson().contains("\"expectedGoals\":\"1.25\""));
        assertNotNull(saved.getFetchedAt());
    }

    @Test
    void ignoresIncompleteSourceIdentity() {
        MatchStatisticsSourceSnapshotRepository repository =
                mock(MatchStatisticsSourceSnapshotRepository.class);
        MatchStatisticsSnapshotPersistenceService service =
                new MatchStatisticsSnapshotPersistenceService(repository, new ObjectMapper());

        service.upsert(new Match(), new Team(), Provider.SOFA_SCORE, Map.of("xg", 1));

        verifyNoInteractions(repository);
    }
}
