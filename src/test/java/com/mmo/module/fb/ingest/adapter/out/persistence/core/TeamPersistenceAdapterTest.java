package com.mmo.module.fb.ingest.adapter.out.persistence.core;

import com.mmo.module.fb.domain.league.entity.Season;
import com.mmo.module.fb.domain.team.entity.Team;
import com.mmo.module.fb.domain.team.entity.TeamExternalId;
import com.mmo.module.fb.domain.team.repository.TeamExternalIdRepository;
import com.mmo.module.fb.domain.team.repository.TeamRepository;
import com.mmo.module.fb.ingest.application.model.provider.ProviderTeam;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.DataSourcePolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamPersistenceAdapterTest {

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private TeamExternalIdRepository teamExternalIdRepository;

    private TeamPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TeamPersistenceAdapter(
                teamRepository, teamExternalIdRepository, new DataSourcePolicy());
    }

    @Test
    void resolvesFootballDataAliasToExistingCanonicalTeam() {
        Team canonicalTeam = team(10L, "Bolton Wanderers");
        when(teamExternalIdRepository.findAllByProvider(Provider.FOOTBALL_DATA)).thenReturn(List.of());
        when(teamRepository.findAll()).thenReturn(List.of(canonicalTeam));

        adapter.persistTeamsForSeason(
                Provider.FOOTBALL_DATA,
                season(),
                List.of(new ProviderTeam("fd-123", "Bolton", "Bolton", null, null, null, null)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<TeamExternalId>> mappings =
                (ArgumentCaptor<Iterable<TeamExternalId>>) (ArgumentCaptor<?>)
                        ArgumentCaptor.forClass(Iterable.class);
        verify(teamExternalIdRepository).saveAll(mappings.capture());
        TeamExternalId mapping = StreamSupport.stream(mappings.getValue().spliterator(), false)
                .findFirst().orElseThrow();

        assertSame(canonicalTeam, mapping.getTeam());
        assertEquals("fd-123", mapping.getExternalId());
        assertEquals(Provider.FOOTBALL_DATA, mapping.getProvider());
        verify(teamRepository).saveAll(any(Iterable.class));
    }

    @Test
    void mapsSofaScoreByCanonicalNameWithoutChangingCoreTeamData() {
        Team canonicalTeam = team(20L, "Bolton Wanderers");
        when(teamExternalIdRepository.findAllByProvider(Provider.SOFA_SCORE)).thenReturn(List.of());
        when(teamExternalIdRepository.findAllByProvider(Provider.FOOTBALL_DATA)).thenReturn(List.of());
        when(teamRepository.findAll()).thenReturn(List.of(canonicalTeam));

        adapter.persistTeamsForSeason(
                Provider.SOFA_SCORE,
                season(),
                List.of(new ProviderTeam("sofa-456", "Bolton Wanderers FC",
                        "Bolton", null, null, null, null)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<TeamExternalId>> mappings =
                (ArgumentCaptor<Iterable<TeamExternalId>>) (ArgumentCaptor<?>)
                        ArgumentCaptor.forClass(Iterable.class);
        verify(teamExternalIdRepository).saveAll(mappings.capture());
        TeamExternalId mapping = StreamSupport.stream(mappings.getValue().spliterator(), false)
                .findFirst().orElseThrow();

        assertSame(canonicalTeam, mapping.getTeam());
        assertEquals("sofa-456", mapping.getExternalId());
        assertEquals(Provider.SOFA_SCORE, mapping.getProvider());
        verify(teamRepository, never()).saveAll(any(Iterable.class));
        assertEquals("Bolton Wanderers", canonicalTeam.getName());
    }

    @Test
    void doesNotCreateSofaScoreTeamWhenCanonicalNameDoesNotExist() {
        when(teamExternalIdRepository.findAllByProvider(Provider.SOFA_SCORE)).thenReturn(List.of());
        when(teamExternalIdRepository.findAllByProvider(Provider.FOOTBALL_DATA)).thenReturn(List.of());
        when(teamRepository.findAll()).thenReturn(List.of(team(30L, "Bolton Wanderers")));

        adapter.persistTeamsForSeason(
                Provider.SOFA_SCORE,
                season(),
                List.of(new ProviderTeam("sofa-unknown", "Unknown FC", "Unknown",
                        null, null, null, null)));

        verify(teamRepository, never()).saveAll(any(Iterable.class));
        verify(teamExternalIdRepository, never()).saveAll(any(Iterable.class));
    }

    private static Season season() {
        return Season.builder().year("2026/2027").build();
    }

    private static Team team(Long id, String name) {
        Team team = Team.builder().name(name).isActive(true).build();
        team.setId(id);
        return team;
    }
}
