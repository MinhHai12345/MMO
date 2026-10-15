package com.mmo.module.fb.ingest.service.impl;

import com.mmo.AppProperties;
import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.domain.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.domain.repository.SeasonRepository;
import com.mmo.module.fb.domain.repository.TeamExternalIdRepository;
import com.mmo.module.fb.ingest.provider.spi.ProviderRegistry;
import com.mmo.module.fb.ingest.service.FixturePersistenceService;
import com.mmo.module.fb.ingest.service.TeamIngestionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FixtureIngestionServiceImplTest {
    @Mock ProviderRegistry providerRegistry;
    @Mock TeamIngestionService teamIngestionService;
    @Mock SeasonRepository seasonRepository;
    @Mock LeagueExternalIdRepository leagueExternalIdRepository;
    @Mock TeamExternalIdRepository teamExternalIdRepository;
    @Mock FixturePersistenceService persistenceService;

    @Test
    void shouldRejectReversedDateRangeBeforeCallingDependencies() {
        AppProperties properties = new AppProperties();
        FixtureIngestionServiceImpl service = new FixtureIngestionServiceImpl(providerRegistry, properties,
                teamIngestionService, seasonRepository, leagueExternalIdRepository,
                teamExternalIdRepository, persistenceService);

        assertThrows(IllegalArgumentException.class, () -> service.syncFixturesForProvider(
                Provider.API_FOOTBALL, LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 14)));

        verifyNoInteractions(providerRegistry, teamIngestionService, seasonRepository,
                leagueExternalIdRepository, teamExternalIdRepository, persistenceService);
    }

    @Test
    void shouldSkipAllWorkWhenApiFootballIsDisabled() {
        AppProperties properties = new AppProperties();
        properties.getApiFootball().setEnabled(false);
        FixtureIngestionServiceImpl service = new FixtureIngestionServiceImpl(providerRegistry, properties,
                teamIngestionService, seasonRepository, leagueExternalIdRepository,
                teamExternalIdRepository, persistenceService);

        service.syncFixturesForProvider(Provider.API_FOOTBALL, null, null);

        verifyNoInteractions(providerRegistry, teamIngestionService, seasonRepository,
                leagueExternalIdRepository, teamExternalIdRepository, persistenceService);
    }
}