package com.mmo.module.fb.ingest.application.service.masterdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmo.module.fb.domain.league.entity.League;
import com.mmo.module.fb.domain.league.entity.LeagueExternalId;
import com.mmo.module.fb.domain.league.repository.LeagueRepository;
import com.mmo.module.fb.domain.league.repository.SeasonRepository;
import com.mmo.module.fb.domain.team.repository.TeamRepository;
import com.mmo.module.fb.ingest.application.port.out.ExternalIdMappingPort;
import com.mmo.module.fb.ingest.enums.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MasterDataInitializationServiceTest {

    private LeagueRepository leagueRepository;
    private SeasonRepository seasonRepository;
    private TeamRepository teamRepository;
    private ExternalIdMappingPort externalIdMappingPort;
    private MasterDataInitializationService service;

    @BeforeEach
    void setUp() {
        leagueRepository = mock(LeagueRepository.class);
        seasonRepository = mock(SeasonRepository.class);
        teamRepository = mock(TeamRepository.class);
        externalIdMappingPort = mock(ExternalIdMappingPort.class);
        service = new MasterDataInitializationService(new ObjectMapper().findAndRegisterModules(),
                leagueRepository, seasonRepository, teamRepository, externalIdMappingPort);

        when(leagueRepository.findBySlugIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(leagueRepository.findAllByNameIgnoreCase(anyString())).thenReturn(List.of());
        when(externalIdMappingPort.findLeague(any(Provider.class), anyString())).thenReturn(Optional.empty());
    }

    @Test
    void previewShouldValidateCuratedLeagueCatalogueWithoutWritingToDatabase() {
        var report = service.preview();

        assertEquals("DRY_RUN", report.mode());
        assertEquals(6, report.leaguesToCreate());
        assertEquals(0, report.seasonsToCreate());
        assertEquals(0, report.teamsToCreate());
        assertEquals(18, report.mappingsToRegister());
        org.junit.jupiter.api.Assertions.assertTrue(report.valid(), report.validationErrors().toString());

        verify(leagueRepository, never()).save(any(League.class));
        verify(externalIdMappingPort, never()).registerLeague(any(), any(), anyString());
        verifyNoInteractions(seasonRepository, teamRepository);
    }

    @Test
    void applyShouldCreateCanonicalLeaguesAndRegisterVerifiedProviderMappings() {
        when(leagueRepository.save(any(League.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(externalIdMappingPort.registerLeague(any(League.class), any(Provider.class), anyString()))
                .thenAnswer(invocation -> LeagueExternalId.builder()
                        .league(invocation.getArgument(0))
                        .provider(invocation.getArgument(1))
                        .externalId(invocation.getArgument(2))
                        .build());

        var report = service.apply();

        assertEquals("APPLIED", report.mode());
        assertEquals(6, report.leaguesExisting());
        assertEquals(18, report.mappingsToRegister());
        verify(leagueRepository, times(6)).save(any(League.class));
        verify(externalIdMappingPort, times(18)).registerLeague(any(League.class), any(Provider.class), anyString());
        verifyNoInteractions(seasonRepository, teamRepository);
    }
}
