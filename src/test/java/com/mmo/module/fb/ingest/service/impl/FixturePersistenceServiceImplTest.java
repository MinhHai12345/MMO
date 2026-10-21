package com.mmo.module.fb.ingest.service.impl;

import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.entity.MatchExternalId;
import com.mmo.module.fb.domain.entity.Season;
import com.mmo.module.fb.domain.entity.Team;
import com.mmo.module.fb.domain.enums.MatchStatus;
import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.domain.repository.MatchExternalIdRepository;
import com.mmo.module.fb.domain.repository.MatchRepository;
import com.mmo.module.fb.domain.repository.SeasonRepository;
import com.mmo.module.fb.domain.repository.TeamRepository;
import com.mmo.module.fb.domain.service.ExternalIdMappingService;
import com.mmo.module.fb.ingest.provider.model.ProviderFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FixturePersistenceServiceImplTest {

    @Mock private SeasonRepository seasonRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private MatchRepository matchRepository;
    @Mock private MatchExternalIdRepository matchExternalIdRepository;
    @Mock private ExternalIdMappingService externalIdMappingService;

    @InjectMocks
    private FixturePersistenceServiceImpl service;

    private Season season;
    private Team home;
    private Team away;

    @BeforeEach
    void setUp() {
        season = new Season();
        season.setId(10L);
        home = new Team();
        home.setId(20L);
        away = new Team();
        away.setId(30L);

        lenient().when(seasonRepository.findById(10L)).thenReturn(Optional.of(season));
        lenient().when(teamRepository.findById(20L)).thenReturn(Optional.of(home));
        lenient().when(teamRepository.findById(30L)).thenReturn(Optional.of(away));
    }

    @Test
    void rejectsFixtureWithoutExternalId() {
        ProviderFixture fixture = fixture(" ", "100", "200", Instant.parse("2026-10-21T18:00:00Z"),
                "SCHEDULED", null, null);

        assertFalse(service.persistFixture(10L, 20L, 30L, Provider.API_FOOTBALL, fixture));

        verifyNoInteractions(matchRepository, matchExternalIdRepository, externalIdMappingService);
    }

    @Test
    void updatesExistingFixtureWithoutDowngradingFinishedStatusOrErasingScores() {
        Match match = new Match();
        match.setId(40L);
        match.setSeason(season);
        match.setHomeTeam(home);
        match.setAwayTeam(away);
        match.setMatchTime(Instant.parse("2026-10-20T18:00:00Z"));
        match.setStatus(MatchStatus.FINISHED);
        match.setHomeScore(2);
        match.setAwayScore(1);

        MatchExternalId mapping = new MatchExternalId();
        mapping.setMatch(match);
        when(matchExternalIdRepository.findByProviderAndExternalId(Provider.API_FOOTBALL, "fixture-40"))
                .thenReturn(Optional.of(mapping));
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProviderFixture fixture = fixture("fixture-40", "100", "200",
                Instant.parse("2026-10-20T18:00:00Z"), "SCHEDULED", null, null);

        assertTrue(service.persistFixture(10L, 20L, 30L, Provider.API_FOOTBALL, fixture));

        assertEquals(MatchStatus.FINISHED, match.getStatus());
        assertEquals(2, match.getHomeScore());
        assertEquals(1, match.getAwayScore());
        verify(externalIdMappingService).registerMatch(match, Provider.API_FOOTBALL, "fixture-40");
    }

    @Test
    void rejectsNewFixtureWhenKickoffIsMissing() {
        when(matchExternalIdRepository.findByProviderAndExternalId(Provider.API_FOOTBALL, "fixture-41"))
                .thenReturn(Optional.empty());

        ProviderFixture fixture = fixture("fixture-41", "100", "200", null,
                "SCHEDULED", null, null);

        assertFalse(service.persistFixture(10L, 20L, 30L, Provider.API_FOOTBALL, fixture));

        verify(matchRepository, never()).save(any());
        verify(externalIdMappingService, never()).registerMatch(any(), any(), any());
    }

    private static ProviderFixture fixture(String externalId, String homeTeamId, String awayTeamId,
                                           Instant kickoff, String status,
                                           Integer homeScore, Integer awayScore) {
        return new ProviderFixture(externalId, homeTeamId, awayTeamId, kickoff,
                null, null, null, status, homeScore, awayScore);
    }
}
