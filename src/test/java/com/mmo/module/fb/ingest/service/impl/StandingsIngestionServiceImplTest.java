package com.mmo.module.fb.ingest.service.impl;

import com.mmo.AppProperties;
import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.domain.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.domain.repository.SeasonRepository;
import com.mmo.module.fb.domain.repository.TeamExternalIdRepository;
import com.mmo.module.fb.ingest.provider.spi.ProviderRegistry;
import com.mmo.module.fb.ingest.service.StandingsPersistenceService;
import com.mmo.module.fb.ingest.service.TeamIngestionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StandingsIngestionServiceImplTest {
    @Mock ProviderRegistry providerRegistry;
    @Mock TeamIngestionService teamIngestionService;
    @Mock SeasonRepository seasonRepository;
    @Mock LeagueExternalIdRepository leagueExternalIdRepository;
    @Mock TeamExternalIdRepository teamExternalIdRepository;
    @Mock StandingsPersistenceService persistenceService;

    @Test
    void shouldSkipAllWorkWhenApiFootballIsDisabled() {
        AppProperties properties = new AppProperties();
        properties.getApiFootball().setEnabled(false);
        StandingsIngestionServiceImpl service = new StandingsIngestionServiceImpl(providerRegistry, properties,
                teamIngestionService, seasonRepository, leagueExternalIdRepository,
                teamExternalIdRepository, persistenceService);

        service.syncStandingsForProvider(Provider.API_FOOTBALL);

        verifyNoInteractions(providerRegistry, teamIngestionService, seasonRepository,
                leagueExternalIdRepository, teamExternalIdRepository, persistenceService);
    }
}