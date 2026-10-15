package com.mmo.module.fb.ingest.service.impl;

import com.mmo.AppProperties;
import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.ingest.provider.spi.ProviderRegistry;
import com.mmo.module.fb.ingest.service.LeaguePersistenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeagueIngestionServiceImplTest {
    @Mock ProviderRegistry providerRegistry;
    @Mock LeaguePersistenceService persistenceService;
    @InjectMocks LeagueIngestionServiceImpl service;

    @Test
    void shouldSkipApiFootballWhenProviderIsDisabled() {
        AppProperties properties = new AppProperties();
        properties.getApiFootball().setEnabled(false);
        service = new LeagueIngestionServiceImpl(providerRegistry, properties, persistenceService);

        service.syncLeaguesForProvider(Provider.API_FOOTBALL);

        verifyNoInteractions(providerRegistry, persistenceService);
    }

    @Test
    void shouldSkipApiFootballWhenNoLeagueIdsConfigured() {
        AppProperties properties = new AppProperties();
        properties.getApiFootball().setLeagueIds(java.util.List.of());
        service = new LeagueIngestionServiceImpl(providerRegistry, properties, persistenceService);

        service.syncLeaguesForProvider(Provider.API_FOOTBALL);

        verifyNoInteractions(providerRegistry, persistenceService);
    }
}