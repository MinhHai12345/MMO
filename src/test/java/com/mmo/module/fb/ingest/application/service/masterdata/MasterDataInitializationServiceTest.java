package com.mmo.module.fb.ingest.application.service.masterdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmo.module.fb.domain.league.entity.League;
import com.mmo.module.fb.domain.league.entity.LeagueExternalId;
import com.mmo.module.fb.domain.league.entity.Season;
import com.mmo.module.fb.domain.league.entity.SeasonExternalId;
import com.mmo.module.fb.domain.league.repository.LeagueRepository;
import com.mmo.module.fb.domain.league.repository.SeasonRepository;
import com.mmo.module.fb.domain.team.repository.TeamRepository;
import com.mmo.module.fb.ingest.application.port.out.ExternalIdMappingPort;
import com.mmo.module.fb.ingest.enums.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
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
        assertEquals(78, report.seasonsToCreate());
        assertEquals(0, report.teamsToCreate());
        assertEquals(174, report.mappingsToRegister());
        org.junit.jupiter.api.Assertions.assertTrue(report.valid(), report.validationErrors().toString());

        verify(leagueRepository, never()).save(any(League.class));
        verify(externalIdMappingPort, never()).registerLeague(any(), any(), anyString());
        verifyNoInteractions(seasonRepository, teamRepository);
    }

    @Test
    void applyShouldCreateCanonicalLeaguesAndRegisterVerifiedProviderMappings() {
        when(leagueRepository.save(any(League.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(seasonRepository.save(any(Season.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(externalIdMappingPort.registerLeague(any(League.class), any(Provider.class), anyString()))
                .thenAnswer(invocation -> LeagueExternalId.builder()
                        .league(invocation.getArgument(0))
                        .provider(invocation.getArgument(1))
                        .externalId(invocation.getArgument(2))
                        .build());

        var report = service.apply();

        assertEquals("APPLIED", report.mode());
        assertEquals(6, report.leaguesExisting());
        assertEquals(78, report.seasonsExisting());
        assertEquals(174, report.mappingsToRegister());
        verify(leagueRepository, times(6)).save(any(League.class));
        verify(externalIdMappingPort, times(18)).registerLeague(any(League.class), any(Provider.class), anyString());
        verify(seasonRepository, times(78)).save(any(Season.class));
        verify(externalIdMappingPort, times(156)).registerSeason(any(Season.class), any(Provider.class), anyString());
        verifyNoInteractions(teamRepository);
    }

    @Test
    void applyShouldBackfillOnlyMissingDatesOnExistingSeasons() {
        League existingLeague = League.builder().name("Premier League").slug("premier-league").build();
        existingLeague.setId(10L);
        Season existingSeason = Season.builder()
                .league(existingLeague)
                .year("2014")
                .startDate(null)
                .endDate(LocalDate.of(2015, 5, 24))
                .build();

        when(leagueRepository.findBySlugIgnoreCase(anyString())).thenAnswer(invocation ->
                "premier-league".equalsIgnoreCase(invocation.getArgument(0))
                        ? Optional.of(existingLeague) : Optional.empty());
        when(leagueRepository.save(any(League.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(seasonRepository.findByLeague_IdAndYear(any(), anyString())).thenAnswer(invocation ->
                Long.valueOf(10L).equals(invocation.getArgument(0))
                        && "2014".equals(invocation.getArgument(1))
                        ? Optional.of(existingSeason) : Optional.empty());
        when(seasonRepository.save(any(Season.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(externalIdMappingPort.findSeason(any(Provider.class), anyString())).thenReturn(Optional.empty());
        when(externalIdMappingPort.registerLeague(any(League.class), any(Provider.class), anyString()))
                .thenAnswer(invocation -> LeagueExternalId.builder()
                        .league(invocation.getArgument(0))
                        .provider(invocation.getArgument(1))
                        .externalId(invocation.getArgument(2))
                        .build());
        when(externalIdMappingPort.registerSeason(any(Season.class), any(Provider.class), anyString()))
                .thenAnswer(invocation -> SeasonExternalId.builder()
                        .season(invocation.getArgument(0))
                        .provider(invocation.getArgument(1))
                        .externalId(invocation.getArgument(2))
                        .build());

        service.apply();

        assertEquals(LocalDate.of(2014, 8, 16), existingSeason.getStartDate());
        assertEquals(LocalDate.of(2015, 5, 24), existingSeason.getEndDate(),
                "Existing non-null end date must not be overwritten by bootstrap data");
        verify(seasonRepository).save(existingSeason);
    }
}
