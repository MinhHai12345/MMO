package com.mmo.module.fb.ingest.service.impl;

import com.mmo.AppProperties;
import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.ingest.service.FixtureIngestionService;
import com.mmo.module.fb.ingest.service.LeagueIngestionService;
import com.mmo.module.fb.ingest.service.StandingsIngestionService;
import com.mmo.module.fb.ingest.service.TeamIngestionService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class FootballDataBootstrapServiceImplTest {

    private final LeagueIngestionService leagueService = mock(LeagueIngestionService.class);
    private final TeamIngestionService teamService = mock(TeamIngestionService.class);
    private final FixtureIngestionService fixtureService = mock(FixtureIngestionService.class);
    private final StandingsIngestionService standingsService = mock(StandingsIngestionService.class);

    @Test
    void shouldSyncCoreResourcesInDependencyOrder() {
        AppProperties properties = validProperties();
        FootballDataBootstrapServiceImpl service = new FootballDataBootstrapServiceImpl(
                properties, leagueService, teamService, fixtureService, standingsService);

        service.syncCoreData();

        InOrder order = inOrder(leagueService, teamService, fixtureService, standingsService);
        order.verify(leagueService).syncLeaguesForProvider(Provider.API_FOOTBALL);
        order.verify(teamService).syncTeamsForProvider(Provider.API_FOOTBALL);
        order.verify(fixtureService).syncFixturesForProvider(Provider.API_FOOTBALL, null, null);
        order.verify(standingsService).syncStandingsForProvider(Provider.API_FOOTBALL);
        verifyNoMoreInteractions(leagueService, teamService, fixtureService, standingsService);
    }

    @Test
    void shouldFailFastWhenApiKeyIsMissing() {
        AppProperties properties = validProperties();
        properties.getApiFootball().setApiKey("mock");
        FootballDataBootstrapServiceImpl service = new FootballDataBootstrapServiceImpl(
                properties, leagueService, teamService, fixtureService, standingsService);

        assertThrows(IllegalStateException.class, service::syncCoreData);
        verifyNoInteractions(leagueService, teamService, fixtureService, standingsService);
    }

    @Test
    void shouldFailFastWhenNoLeaguesAreConfigured() {
        AppProperties properties = validProperties();
        properties.getApiFootball().setLeagueIds(java.util.List.of());
        FootballDataBootstrapServiceImpl service = new FootballDataBootstrapServiceImpl(
                properties, leagueService, teamService, fixtureService, standingsService);

        assertThrows(IllegalStateException.class, service::syncCoreData);
        verifyNoInteractions(leagueService, teamService, fixtureService, standingsService);
    }

    private static AppProperties validProperties() {
        AppProperties properties = new AppProperties();
        properties.getApiFootball().setEnabled(true);
        properties.getApiFootball().setApiKey("test-api-key");
        properties.getApiFootball().setLeagueIds(java.util.List.of(39L));
        return properties;
    }
}
