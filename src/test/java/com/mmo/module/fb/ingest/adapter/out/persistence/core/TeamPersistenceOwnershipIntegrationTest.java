package com.mmo.module.fb.ingest.adapter.out.persistence.core;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.DataSourcePolicy;
import com.mmo.module.fb.ingest.application.model.provider.ProviderTeam;
import com.mmo.module.fb.domain.league.entity.League;
import com.mmo.module.fb.domain.league.entity.Season;
import com.mmo.module.fb.domain.team.entity.Team;
import com.mmo.module.fb.domain.team.entity.TeamExternalId;
import com.mmo.module.fb.domain.team.repository.TeamExternalIdRepository;
import com.mmo.module.fb.domain.team.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamPersistenceOwnershipIntegrationTest {

    @Mock private TeamRepository teamRepository;
    @Mock private TeamExternalIdRepository teamExternalIdRepository;

    @Test
    void footballDataAdoptsExistingUkFallbackTeamInsteadOfCreatingDuplicate() {
        League league = new League();
        league.setId(39L);
        league.setName("Premier League");
        Season season = new Season();
        season.setId(2024L);
        season.setLeague(league);
        season.setYear("2024/2025");

        Team ukFallback = new Team();
        ukFallback.setId(11L);
        ukFallback.setName("Manchester United");
        ukFallback.setShortName("Man United");
        ukFallback.setLogoUrl("https://uk.example/logo.png");
        TeamExternalId footballDataId = TeamExternalId.builder()
                .team(ukFallback).provider(Provider.FOOTBALL_DATA).externalId("66").build();

        when(teamExternalIdRepository.findAllByProvider(Provider.FOOTBALL_DATA)).thenReturn(List.of());
        when(teamRepository.findAll()).thenReturn(List.of(ukFallback));
        when(teamExternalIdRepository.findByTeamIdAndProvider(11L, Provider.FOOTBALL_DATA))
                .thenReturn(Optional.of(footballDataId));

        TeamPersistenceAdapter persistence = new TeamPersistenceAdapter(teamRepository, teamExternalIdRepository, new DataSourcePolicy());
        persistence.persistTeamsForSeason(Provider.FOOTBALL_DATA, season,
                List.of(new ProviderTeam("66", "Manchester United", "Man Utd",
                        "https://fd.example/logo.png", null, null, null)));

        assertEquals("Manchester United", ukFallback.getName());
        assertEquals("https://fd.example/logo.png", ukFallback.getLogoUrl());
        verify(teamRepository).saveAll(argThat(rows ->
                rows.iterator().hasNext() && rows.iterator().next() == ukFallback));
        verify(teamExternalIdRepository).saveAll(argThat(rows ->
                rows.iterator().hasNext()
                        && "66".equals(((TeamExternalId) rows.iterator().next()).getExternalId())));
    }

    @Test
    void ukSecondaryMappingDoesNotOverwriteFootballDataOwnedTeamMetadata() {
        League league = new League();
        league.setId(39L);
        league.setName("Premier League");
        Season season = new Season();
        season.setId(2024L);
        season.setLeague(league);
        season.setYear("2024/2025");

        Team canonical = new Team();
        canonical.setId(10L);
        canonical.setName("Manchester United");
        canonical.setShortName("Man United");
        canonical.setLogoUrl("https://football-data.example/logo.png");

        TeamExternalId footballDataId = TeamExternalId.builder()
                .team(canonical).provider(Provider.FOOTBALL_DATA).externalId("66").build();
        TeamExternalId ukId = TeamExternalId.builder()
                .team(canonical).provider(Provider.FOOTBALL_DATA_UK).externalId("TEAM:manchesterunited").build();

        when(teamExternalIdRepository.findAllByProvider(Provider.FOOTBALL_DATA_UK)).thenReturn(List.of());
        when(teamExternalIdRepository.findAllByProvider(Provider.FOOTBALL_DATA)).thenReturn(List.of(footballDataId));
        when(teamRepository.findAll()).thenReturn(List.of(canonical));
        when(teamExternalIdRepository.findByTeamIdAndProvider(10L, Provider.FOOTBALL_DATA))
                .thenReturn(Optional.of(footballDataId));
        when(teamExternalIdRepository.findByTeamIdAndProvider(10L, Provider.FOOTBALL_DATA_UK))
                .thenReturn(Optional.of(ukId));

        TeamPersistenceAdapter persistence = new TeamPersistenceAdapter(
                teamRepository, teamExternalIdRepository, externalIdMappingService, new DataSourcePolicy());

        persistence.persistTeamsForSeason(Provider.FOOTBALL_DATA_UK, season,
                List.of(new ProviderTeam("uk-team-1", "Manchester United", "Man Utd",
                        "https://uk.example/new-logo.png", null, null, null)));

        assertEquals("Manchester United", canonical.getName());
        assertEquals("Man United", canonical.getShortName());
        assertEquals("https://football-data.example/logo.png", canonical.getLogoUrl());
        verify(teamRepository, never()).save(any(Team.class));
        verify(teamExternalIdRepository).saveAll(argThat(rows ->
                rows.iterator().hasNext()
                        && "uk-team-1".equals(((TeamExternalId) rows.iterator().next()).getExternalId())));
    }
}
