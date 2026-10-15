package com.mmo.module.fb.ingest.service.impl;

import com.mmo.AppProperties;
import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.domain.repository.MatchExternalIdRepository;
import com.mmo.module.fb.domain.repository.MatchRepository;
import com.mmo.module.fb.ingest.provider.spi.ProviderRegistry;
import com.mmo.module.fb.ingest.service.FixtureIngestionService;
import com.mmo.module.fb.ingest.service.OddsPersistenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OddsIngestionServiceImplTest {
    @Mock ProviderRegistry providerRegistry;
    @Mock FixtureIngestionService fixtureIngestionService;
    @Mock MatchRepository matchRepository;
    @Mock MatchExternalIdRepository matchExternalIdRepository;
    @Mock OddsPersistenceService persistenceService;

    @Test
    void shouldSkipAllWorkWhenApiFootballIsDisabled() {
        AppProperties properties = new AppProperties();
        properties.getIngestion().getProviders().setOdds(Provider.API_FOOTBALL);
        properties.getApiFootball().setEnabled(false);
        OddsIngestionServiceImpl service = new OddsIngestionServiceImpl(providerRegistry, properties,
                fixtureIngestionService, matchRepository, matchExternalIdRepository, persistenceService);

        service.syncUpcomingOdds();

        verifyNoInteractions(providerRegistry, fixtureIngestionService, matchRepository,
                matchExternalIdRepository, persistenceService);
    }
}