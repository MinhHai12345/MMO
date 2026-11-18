package com.mmo.module.fb.ingest.application.service.ingestion;

import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.application.model.provider.ProviderLeague;
import com.mmo.module.fb.ingest.application.port.out.LeagueExternalMappingPort;
import com.mmo.module.fb.ingest.application.port.out.LeaguePersistencePort;
import com.mmo.module.fb.ingest.application.port.out.provider.LeagueIngestionPort;
import com.mmo.module.fb.ingest.application.registry.ProviderIngestionRegistry;
import com.mmo.module.fb.ingest.application.service.support.IngestionServiceSupport;
import com.mmo.module.fb.ingest.enums.Provider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeagueIngestionServiceTest {

    @Mock
    private ProviderIngestionRegistry providerRegistry;
    @Mock
    private AppProperties appProperties;
    @Mock
    private IngestionServiceSupport ingestionSupport;
    @Mock
    private LeaguePersistencePort leaguePersistenceService;
    @Mock
    private LeagueExternalMappingPort leagueExternalMappingPort;
    @Mock
    private LeagueIngestionPort leagueProvider;

    @InjectMocks
    private LeagueIngestionService service;

    @Test
    void shouldDiscoverFullCatalogueForEveryRegisteredLeagueProvider() {
        ProviderLeague league = new ProviderLeague(
                "provider-1", "league-one", "League One", "Country", null, List.of());

        for (Provider provider : List.of(
                Provider.API_FOOTBALL,
                Provider.FOOTBALL_DATA,
                Provider.SOFA_SCORE,
                Provider.UNDER_STATS)) {
            when(providerRegistry.league(provider)).thenReturn(leagueProvider);
            when(leagueProvider.fetchLeagues(List.of())).thenReturn(List.of(league));

            service.syncLeaguesForProvider(provider);

            verify(ingestionSupport).requireProviderEnabled(provider, "league");
            verify(leaguePersistenceService).persistLeagues(provider, List.of(league));
        }
        verify(leagueProvider, org.mockito.Mockito.times(4)).fetchLeagues(List.of());
    }

    @Test
    void shouldRejectEmptyCatalogueInsteadOfReportingSuccessfulIngestion() {
        Provider provider = Provider.SOFA_SCORE;
        when(providerRegistry.league(provider)).thenReturn(leagueProvider);
        when(leagueProvider.fetchLeagues(List.of())).thenReturn(List.of());

        assertThrows(IllegalStateException.class, () -> service.syncLeaguesForProvider(provider));

        verify(leaguePersistenceService, never()).persistLeagues(provider, List.of());
    }
}
