package com.mmo.module.fb.ingest.persistence;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.DataSourcePolicy;
import com.mmo.module.fb.domain.league.entity.League;
import com.mmo.module.fb.domain.league.entity.Season;
import com.mmo.module.fb.domain.league.repository.SeasonRepository;
import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.match.entity.MatchExternalId;
import com.mmo.module.fb.domain.match.enums.MatchProvider;
import com.mmo.module.fb.domain.match.repository.MatchExternalIdRepository;
import com.mmo.module.fb.domain.match.repository.MatchRepository;
import com.mmo.module.fb.domain.team.entity.Team;
import com.mmo.module.fb.domain.team.repository.TeamRepository;
import com.mmo.module.fb.ingest.provider.model.ProviderFixture;
import com.mmo.module.fb.ingest.service.ExternalIdMappingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FixturePersistenceOwnershipIntegrationTest {

    @Mock private SeasonRepository seasonRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private MatchRepository matchRepository;
    @Mock private MatchExternalIdRepository matchExternalIdRepository;
    @Mock private ExternalIdMappingService externalIdMappingService;

    private FixturePersistenceServiceImpl persistence;

    private Season season;
    private Team home;
    private Team away;
    private ProviderFixture ukFixture;

    @BeforeEach
    void setUp() {
        DataSourcePolicy policy = new DataSourcePolicy();
        persistence = new FixturePersistenceServiceImpl(seasonRepository, teamRepository,
                matchRepository, matchExternalIdRepository, externalIdMappingService, policy,
                new SourceAwareMergeService(policy,
                        mock(com.mmo.module.fb.domain.match.repository.MatchFieldProvenanceRepository.class)));

        League league = new League();
        league.setId(39L);
        league.setName("Premier League");
        season = new Season();
        season.setId(2024L);
        season.setLeague(league);
        season.setYear("2024/2025");

        home = new Team();
        home.setId(10L);
        home.setName("Arsenal");
        away = new Team();
        away.setId(11L);
        away.setName("Chelsea");

        ukFixture = new ProviderFixture("uk-fixture-1", "ars", "che",
                Instant.parse("2024-08-01T15:00:00Z"), null, null, null,
                "FT", 2, 1);

        when(seasonRepository.findById(2024L)).thenReturn(Optional.of(season));
        when(teamRepository.findById(10L)).thenReturn(Optional.of(home));
        when(teamRepository.findById(11L)).thenReturn(Optional.of(away));
    }

    @Test
    void allowsUkToCreateMissingOverlapFixtureWhenFootballDataHasNoIdentity() {
        when(matchExternalIdRepository.findByProviderAndExternalId(
                MatchProvider.FOOTBALL_DATA_UK, "uk-fixture-1")).thenReturn(Optional.empty());
        when(matchRepository.findFixtureCandidates(eq(2024L), eq(10L), eq(11L), any(), any()))
                .thenReturn(List.of());
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> {
            Match saved = invocation.getArgument(0);
            saved.setId(900L);
            return saved;
        });

        boolean saved = persistence.persistFixture(2024L, 10L, 11L,
                Provider.FOOTBALL_DATA_UK, ukFixture);

        assertTrue(saved);
        verify(matchRepository).save(argThat(match ->
                match.getHomeScore().equals(2) && match.getAwayScore().equals(1)));
        verify(externalIdMappingService).registerMatch(any(Match.class),
                eq(Provider.FOOTBALL_DATA_UK), eq("uk-fixture-1"));
    }

    @Test
    void footballDataAdoptsExistingUkFallbackFixtureInsteadOfCreatingDuplicate() {
        when(matchExternalIdRepository.findByProviderAndExternalId(
                MatchProvider.FOOTBALL_DATA, "fd-fixture-1")).thenReturn(Optional.empty());
        Match ukFallback = Match.builder().season(season).homeTeam(home).awayTeam(away)
                .matchTime(ukFixture.kickoff()).build();
        ukFallback.setId(902L);
        when(matchRepository.findFixtureCandidates(eq(2024L), eq(10L), eq(11L), any(), any()))
                .thenReturn(List.of(ukFallback));
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProviderFixture footballDataFixture = new ProviderFixture("fd-fixture-1", "fd-ars", "fd-che",
                ukFixture.kickoff(), "Matchday 1", null, null, "FT", 2, 1);
        boolean saved = persistence.persistFixture(2024L, 10L, 11L,
                Provider.FOOTBALL_DATA, footballDataFixture);

        assertTrue(saved);
        assertEquals(902L, ukFallback.getId());
        verify(matchRepository, times(1)).save(ukFallback);
        verify(externalIdMappingService).registerMatch(ukFallback,
                Provider.FOOTBALL_DATA, "fd-fixture-1");
    }

    @Test
    void rejectsUkCoreOverwriteWhenFootballDataOwnsMatchedFixture() {
        when(matchExternalIdRepository.findByProviderAndExternalId(
                MatchProvider.FOOTBALL_DATA_UK, "uk-fixture-1")).thenReturn(Optional.empty());
        Match canonical = Match.builder().season(season).homeTeam(home).awayTeam(away)
                .matchTime(ukFixture.kickoff()).build();
        canonical.setId(901L);
        canonical.setHomeScore(1);
        canonical.setAwayScore(0);
        when(matchRepository.findFixtureCandidates(eq(2024L), eq(10L), eq(11L), any(), any()))
                .thenReturn(List.of(canonical));
        MatchExternalId footballDataIdentity = new MatchExternalId();
        when(matchExternalIdRepository.existsByMatchIdAndProvider(901L, MatchProvider.FOOTBALL_DATA))
                .thenReturn(true);

        boolean saved = persistence.persistFixture(2024L, 10L, 11L,
                Provider.FOOTBALL_DATA_UK, ukFixture);

        assertFalse(saved);
        assertEquals(1, canonical.getHomeScore());
        assertEquals(0, canonical.getAwayScore());
        verify(matchRepository, never()).save(any(Match.class));
    }
}
