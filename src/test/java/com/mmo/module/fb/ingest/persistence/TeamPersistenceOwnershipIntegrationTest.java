package com.mmo.module.fb.ingest.persistence;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.DataSourcePolicy;
import com.mmo.module.fb.ingest.provider.model.ProviderTeam;
import com.mmo.module.fb.ingest.service.ExternalIdMappingService;
import com.mmo.module.fb.league.entity.League;
import com.mmo.module.fb.league.entity.Season;
import com.mmo.module.fb.team.entity.Team;
import com.mmo.module.fb.team.entity.TeamExternalId;
import com.mmo.module.fb.team.repository.TeamExternalIdRepository;
import com.mmo.module.fb.team.repository.TeamRepository;
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
    @Mock private ExternalIdMappingService externalIdMappingService;

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
        when(teamRepository.findAll()).thenReturn(List.of(canonical));
        when(teamExternalIdRepository.findByTeamIdAndProvider(10L, Provider.FOOTBALL_DATA))
                .thenReturn(Optional.of(footballDataId));
        when(teamExternalIdRepository.findByTeamIdAndProvider(10L, Provider.FOOTBALL_DATA_UK))
                .thenReturn(Optional.empty(), Optional.of(ukId));

        TeamPersistenceServiceImpl persistence = new TeamPersistenceServiceImpl(
                teamRepository, teamExternalIdRepository, externalIdMappingService, new DataSourcePolicy());

        persistence.persistTeamsForSeason(Provider.FOOTBALL_DATA_UK, season,
                List.of(new ProviderTeam("uk-team-1", "Manchester United", "Man Utd",
                        "https://uk.example/new-logo.png", null, null, null)));

        assertEquals("Manchester United", canonical.getName());
        assertEquals("Man United", canonical.getShortName());
        assertEquals("https://football-data.example/logo.png", canonical.getLogoUrl());
        verify(teamRepository, never()).save(any(Team.class));
        verify(externalIdMappingService).registerTeam(canonical, Provider.FOOTBALL_DATA_UK,
                "uk-team-1");
    }
}
