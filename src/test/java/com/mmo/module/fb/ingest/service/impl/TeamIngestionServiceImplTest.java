package com.mmo.module.fb.ingest.service.impl;

import com.mmo.AppProperties;
import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.domain.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.domain.repository.SeasonRepository;
import com.mmo.module.fb.ingest.provider.spi.ProviderRegistry;
import com.mmo.module.fb.ingest.service.LeagueIngestionService;
import com.mmo.module.fb.ingest.service.TeamPersistenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamIngestionServiceImplTest {
    @Mock ProviderRegistry providerRegistry;
    @Mock LeagueIngestionService leagueIngestionService;
    @Mock SeasonRepository seasonRepository;
    @Mock LeagueExternalIdRepository leagueExternalIdRepository;
    @Mock TeamPersistenceService persistenceService;

    @Test
    void shouldSkipAllWorkWhenApiFootballIsDisabled() {
        AppProperties properties = new AppProperties();
        properties.getApiFootball().setEnabled(false);
        TeamIngestionServiceImpl service = new TeamIngestionServiceImpl(providerRegistry, properties,
                leagueIngestionService, seasonRepository, leagueExternalIdRepository, persistenceService);

        service.syncTeamsForProvider(Provider.API_FOOTBALL);

        verifyNoInteractions(providerRegistry, leagueIngestionService, seasonRepository,
                leagueExternalIdRepository, persistenceService);
    }
}