package com.mmo.module.fb.ingest.persistence;

import com.mmo.module.fb.domain.league.entity.League;
import com.mmo.module.fb.domain.league.entity.Season;
import com.mmo.module.fb.domain.league.repository.SeasonRepository;
import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.match.enums.MatchProvider;
import com.mmo.module.fb.domain.match.repository.MatchExternalIdRepository;
import com.mmo.module.fb.domain.match.repository.MatchFieldProvenanceRepository;
import com.mmo.module.fb.domain.match.repository.MatchRepository;
import com.mmo.module.fb.domain.team.entity.Team;
import com.mmo.module.fb.domain.team.repository.TeamRepository;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.DataSourcePolicy;
import com.mmo.module.fb.ingest.provider.model.ProviderFixture;
import com.mmo.module.fb.ingest.service.ResolvedProviderFixture;
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
    @Mock private MatchFieldProvenanceRepository provenanceRepository;

    private FixturePersistenceServiceImpl persistence;
    private Season season;
    private Team home;
    private Team away;
    private ProviderFixture fixture;

    @BeforeEach
    void setUp() {
        DataSourcePolicy policy = new DataSourcePolicy();
        persistence = new FixturePersistenceServiceImpl(seasonRepository, teamRepository,
                matchRepository, matchExternalIdRepository, policy,
                new SourceAwareMergeService(policy, provenanceRepository));

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

        fixture = new ProviderFixture("uk-fixture-1", "ars", "che",
                Instant.parse("2024-08-01T15:00:00Z"), null, null, null,
                "FT", 2, 1);

        when(seasonRepository.findById(2024L)).thenReturn(Optional.of(season));
        lenient().when(teamRepository.findAllById(anyList())).thenReturn(List.of(home, away));
        lenient().when(matchExternalIdRepository.findAllByProviderAndExternalIdIn(
                any(MatchProvider.class), anyList())).thenReturn(List.of());
        lenient().when(matchRepository.findBatchFixtureCandidates(
                eq(2024L), anyList(), any(), any())).thenReturn(List.of());
        lenient().when(matchExternalIdRepository.findAllByMatchIdInAndProvider(
                anyList(), any(MatchProvider.class))).thenReturn(List.of());
    }

    @Test
    void createsCoreFixtureAndPersistsItsProviderIdentityInBatch() {
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> {
            Match saved = invocation.getArgument(0);
            saved.setId(900L);
            return saved;
        });

        int saved = persistence.persistFixtures(2024L, Provider.FOOTBALL_DATA_UK,
                List.of(new ResolvedProviderFixture(10L, 11L, fixture)));

        assertEquals(1, saved);
        verify(matchRepository, atLeastOnce()).save(argThat(match ->
                Integer.valueOf(2).equals(match.getHomeScore())
                        && Integer.valueOf(1).equals(match.getAwayScore())));
        verify(matchExternalIdRepository).saveAll(argThat(mappings ->
                count(mappings) == 1
                        && "uk-fixture-1".equals(mappings.iterator().next().getExternalId())));
    }

    @Test
    void adoptsUniqueExistingCanonicalFixtureAndDoesNotCreateDuplicate() {
        Match ukFallback = Match.builder().season(season).homeTeam(home).awayTeam(away)
                .matchTime(fixture.kickoff()).build();
        ukFallback.setId(902L);
        when(matchRepository.findBatchFixtureCandidates(eq(2024L), anyList(), any(), any()))
                .thenReturn(List.of(ukFallback));
        when(matchExternalIdRepository.findAllByMatchIdInAndProvider(
                List.of(902L), MatchProvider.FOOTBALL_DATA)).thenReturn(List.of());
        when(matchExternalIdRepository.findAllByMatchIdInAndProvider(
                List.of(902L), MatchProvider.FOOTBALL_DATA_UK)).thenReturn(List.of());

        ProviderFixture footballDataFixture = new ProviderFixture("fd-fixture-1", "fd-ars", "fd-che",
                fixture.kickoff(), "Matchday 1", null, null, "FT", 2, 1);
        int saved = persistence.persistFixtures(2024L, Provider.FOOTBALL_DATA,
                List.of(new ResolvedProviderFixture(10L, 11L, footballDataFixture)));

        assertEquals(1, saved);
        assertEquals(902L, ukFallback.getId());
        verify(matchRepository).save(ukFallback);
        verify(matchExternalIdRepository).saveAll(argThat(mappings ->
                mappings.size() == 1
                        && "fd-fixture-1".equals(mappings.iterator().next().getExternalId())));
    }

    @Test
    void secondaryProviderCanAttachIdentityButCannotOverwritePrimaryCoreFields() {
        Match canonical = Match.builder().season(season).homeTeam(home).awayTeam(away)
                .matchTime(fixture.kickoff()).build();
        canonical.setId(901L);
        canonical.setHomeScore(1);
        canonical.setAwayScore(0);
        when(matchRepository.findBatchFixtureCandidates(eq(2024L), anyList(), any(), any()))
                .thenReturn(List.of(canonical));
        when(matchExternalIdRepository.findAllByMatchIdInAndProvider(
                List.of(901L), MatchProvider.FOOTBALL_DATA)).thenReturn(List.of(
                com.mmo.module.fb.domain.match.entity.MatchExternalId.builder()
                        .match(canonical).provider(MatchProvider.FOOTBALL_DATA)
                        .externalId("fd-fixture-1").build()));

        int saved = persistence.persistFixtures(2024L, Provider.FOOTBALL_DATA_UK,
                List.of(new ResolvedProviderFixture(10L, 11L, fixture)));

        assertEquals(1, saved, "secondary source identity may be attached to the canonical fixture");
        assertEquals(1, canonical.getHomeScore());
        assertEquals(0, canonical.getAwayScore());
        verify(matchRepository, never()).save(any(Match.class));
        verify(matchExternalIdRepository).saveAll(argThat(mappings -> mappings.size() == 1));
    }

    @Test
    void skipsAmbiguousCandidatesWithoutCreatingMatchOrIdentity() {
        Match first = candidate(910L);
        Match second = candidate(911L);
        when(matchRepository.findBatchFixtureCandidates(eq(2024L), anyList(), any(), any()))
                .thenReturn(List.of(first, second));

        int saved = persistence.persistFixtures(2024L, Provider.FOOTBALL_DATA_UK,
                List.of(new ResolvedProviderFixture(10L, 11L, fixture)));

        assertEquals(0, saved);
        verify(matchRepository, never()).save(any(Match.class));
        verify(matchExternalIdRepository, never()).saveAll(anyList());
    }

    @Test
    void deduplicatesRepeatedProviderExternalIdInsideOneBatch() {
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> {
            Match saved = invocation.getArgument(0);
            saved.setId(920L);
            return saved;
        });

        int saved = persistence.persistFixtures(2024L, Provider.FOOTBALL_DATA_UK, List.of(
                new ResolvedProviderFixture(10L, 11L, fixture),
                new ResolvedProviderFixture(10L, 11L, fixture)));

        assertEquals(1, saved);
        verify(matchExternalIdRepository).saveAll(argThat(mappings -> mappings.size() == 1));
    }

    private static long count(Iterable<?> values) {
        long count = 0;
        for (Object ignored : values) count++;
        return count;
    }

    private Match candidate(Long id) {
        Match match = Match.builder().season(season).homeTeam(home).awayTeam(away)
                .matchTime(fixture.kickoff()).build();
        match.setId(id);
        return match;
    }
}
