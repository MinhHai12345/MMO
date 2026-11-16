package com.mmo.module.fb.ingest.application.service.masterdata;

import com.mmo.module.fb.domain.league.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.domain.league.repository.LeagueRepository;
import com.mmo.module.fb.domain.league.repository.SeasonExternalIdRepository;
import com.mmo.module.fb.domain.league.repository.SeasonRepository;
import com.mmo.module.fb.domain.team.repository.TeamExternalIdRepository;
import com.mmo.module.fb.domain.team.repository.TeamRepository;
import com.mmo.module.fb.ingest.enums.Provider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MasterDataExportServiceTest {

    @Mock private LeagueRepository leagueRepository;
    @Mock private SeasonRepository seasonRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private LeagueExternalIdRepository leagueExternalIdRepository;
    @Mock private SeasonExternalIdRepository seasonExternalIdRepository;
    @Mock private TeamExternalIdRepository teamExternalIdRepository;

    @InjectMocks
    private MasterDataExportService service;

    @Test
    void shouldExportEmptyCanonicalCatalogueWithoutLosingProviderCoverage() {
        when(leagueRepository.findAll()).thenReturn(List.of());
        when(seasonRepository.findAll()).thenReturn(List.of());
        when(teamRepository.findAll()).thenReturn(List.of());

        for (Provider provider : Provider.values()) {
            when(leagueExternalIdRepository.findAllByProvider(provider)).thenReturn(List.of());
            when(seasonExternalIdRepository.findAllByProvider(provider)).thenReturn(List.of());
            when(teamExternalIdRepository.findAllByProvider(provider)).thenReturn(List.of());
        }

        var exported = service.export();

        assertNotNull(exported);
        assertTrue(exported.leagues().isEmpty());
        assertTrue(exported.teams().isEmpty());
        for (Provider provider : Provider.values()) {
            verify(leagueExternalIdRepository).findAllByProvider(provider);
            verify(seasonExternalIdRepository).findAllByProvider(provider);
            verify(teamExternalIdRepository).findAllByProvider(provider);
        }
    }
}
