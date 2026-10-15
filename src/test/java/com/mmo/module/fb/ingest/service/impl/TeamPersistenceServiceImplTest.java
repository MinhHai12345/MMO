package com.mmo.module.fb.ingest.service.impl;

import com.mmo.module.fb.domain.entity.Team;
import com.mmo.module.fb.domain.entity.TeamExternalId;
import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.domain.repository.TeamExternalIdRepository;
import com.mmo.module.fb.domain.repository.TeamRepository;
import com.mmo.module.fb.ingest.provider.model.ProviderTeam;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamPersistenceServiceImplTest {

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private TeamExternalIdRepository teamExternalIdRepository;

    @InjectMocks
    private TeamPersistenceServiceImpl service;

    @Test
    void shouldNotReassignExistingProviderIdForCanonicalTeam() {
        Team existingTeam = new Team();
        existingTeam.setId(10L);
        existingTeam.setName("Arsenal");

        TeamExternalId existingMapping = TeamExternalId.builder()
                .team(existingTeam)
                .provider(Provider.API_FOOTBALL)
                .externalId("old-provider-id")
                .build();

        when(teamRepository.findAll()).thenReturn(List.of(existingTeam));
        when(teamExternalIdRepository.findAllByProvider(Provider.API_FOOTBALL))
                .thenReturn(List.of(existingMapping));

        service.persistTeamsForSeason(
                Provider.API_FOOTBALL,
                null,
                List.of(new ProviderTeam("new-provider-id", "Arsenal", "ARS", null, null, null, null)));

        verify(teamRepository, never()).save(any(Team.class));
        verify(teamExternalIdRepository, never()).save(any(TeamExternalId.class));
    }
}
