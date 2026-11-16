package com.mmo.module.fb.ingest.application.service.masterdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmo.module.fb.domain.league.repository.LeagueRepository;
import com.mmo.module.fb.domain.league.repository.SeasonRepository;
import com.mmo.module.fb.domain.team.repository.TeamRepository;
import com.mmo.module.fb.ingest.application.service.masterdata.MasterDataInitializationService;
import com.mmo.module.fb.ingest.application.port.out.ExternalIdMappingPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

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
    }

    @Test
    void previewShouldReadEmptyTemplateWithoutWritingToDatabase() {
        var report = service.preview();

        assertEquals("DRY_RUN", report.mode());
        assertEquals(0, report.leaguesToCreate());
        assertEquals(0, report.teamsToCreate());
        assertEquals(0, report.mappingsToRegister());
        verifyNoInteractions(leagueRepository, seasonRepository, teamRepository, externalIdMappingPort);
    }

    @Test
    void applyShouldBeIdempotentForAnEmptyMasterDataFile() {
        var report = service.apply();

        assertEquals("APPLIED", report.mode());
        verifyNoInteractions(leagueRepository, seasonRepository, teamRepository, externalIdMappingPort);
    }
}
