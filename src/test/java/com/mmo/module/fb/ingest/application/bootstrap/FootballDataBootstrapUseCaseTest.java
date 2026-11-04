package com.mmo.module.fb.ingest.application.bootstrap;

import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.application.lock.IngestionDistributedLock;
import com.mmo.module.fb.ingest.application.port.out.FootballDataUkIngestionPort;
import com.mmo.module.fb.ingest.application.port.out.ProviderCheckpointStore;
import com.mmo.module.fb.ingest.enums.Provider;

import com.mmo.module.fb.ingest.service.FixtureIngestionService;
import com.mmo.module.fb.ingest.service.LeagueIngestionService;
import com.mmo.module.fb.ingest.service.ProviderOverrides;
import com.mmo.module.fb.ingest.service.SeasonIngestionService;
import com.mmo.module.fb.ingest.service.StandingsIngestionService;
import com.mmo.module.fb.ingest.service.TeamIngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;



import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FootballDataBootstrapUseCaseTest {

    private IngestionDistributedLock lock;
    private AppProperties properties;
    private ProviderCheckpointStore checkpoints;
    private FootballDataUkIngestionPort footballDataUk;
    private LeagueIngestionService leagues;
    private SeasonIngestionService seasons;
    private TeamIngestionService teams;
    private FixtureIngestionService fixtures;
    private StandingsIngestionService standings;
    private FootballDataBootstrapUseCase service;

    @BeforeEach
    void setUp() {
        lock = mock(IngestionDistributedLock.class);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return true;
        }).when(lock).execute(anyString(), any(Runnable.class));

        properties = new AppProperties();
        AppProperties.ProviderConfig apiConfig = new AppProperties.ProviderConfig();
        apiConfig.setEnabled(true);
        apiConfig.setApiKey("test-api-key");
        properties.getIngestion().getProviderConfig().put(Provider.API_FOOTBALL, apiConfig);
        AppProperties.ProviderConfig ukConfig = new AppProperties.ProviderConfig();
        ukConfig.setEnabled(false);
        properties.getIngestion().getProviderConfig().put(Provider.FOOTBALL_DATA_UK, ukConfig);
        properties.getIngestion().getProviders().setLeague(Provider.API_FOOTBALL);
        properties.getIngestion().getProviders().setSeason(Provider.API_FOOTBALL);
        properties.getIngestion().getProviders().setTeam(Provider.API_FOOTBALL);
        properties.getIngestion().getProviders().setFixture(Provider.API_FOOTBALL);

        checkpoints = mock(ProviderCheckpointStore.class);
        footballDataUk = mock(FootballDataUkIngestionPort.class);
        leagues = mock(LeagueIngestionService.class);
        seasons = mock(SeasonIngestionService.class);
        teams = mock(TeamIngestionService.class);
        fixtures = mock(FixtureIngestionService.class);
        standings = mock(StandingsIngestionService.class);

        when(checkpoints.isCompleted(any(), anyString())).thenReturn(false);

        service = new FootballDataBootstrapUseCase(
                lock, properties, checkpoints, footballDataUk,
                leagues, seasons, teams, fixtures, standings);
    }

    @Test
    void recordsTeamCheckpointButNotFixtureCheckpointWhenFixtureBackfillFails() {
        doThrow(new IllegalStateException("fixture provider unavailable"))
                .when(fixtures).syncAllSeasonsForProvider(Provider.API_FOOTBALL, null, null);

        assertThrows(IllegalStateException.class, () -> service.syncCoreData(
                new ProviderOverrides(Provider.API_FOOTBALL, Provider.API_FOOTBALL,
                        Provider.API_FOOTBALL, Provider.API_FOOTBALL, null)));

        verify(checkpoints).markCompleted(Provider.API_FOOTBALL, "CORE_BACKFILL_TEAMS");
        verify(checkpoints, never()).markCompleted(Provider.API_FOOTBALL, "CORE_BACKFILL_FIXTURES");
        verify(teams).syncAllSeasonsForProvider(Provider.API_FOOTBALL);
        verify(fixtures).syncAllSeasonsForProvider(Provider.API_FOOTBALL, null, null);
        verify(footballDataUk, never()).syncInitialData();
    }

    @Test
    void completedResourceCheckpointsMakeBootstrapNoOp() {
        when(checkpoints.isCompleted(Provider.API_FOOTBALL, "CORE_BACKFILL_TEAMS")).thenReturn(true);
        when(checkpoints.isCompleted(Provider.API_FOOTBALL, "CORE_BACKFILL_FIXTURES")).thenReturn(true);

        service.syncCoreData(new ProviderOverrides(Provider.API_FOOTBALL, Provider.API_FOOTBALL,
                Provider.API_FOOTBALL, Provider.API_FOOTBALL, null));

        verifyNoInteractions(leagues, seasons, teams, fixtures, footballDataUk);
    }

    @Test
    void doesNotStartWorkflowWhenGlobalLockIsBusy() {
        when(lock.execute(eq(IngestionDistributedLock.GLOBAL_INGEST_LOCK), any(Runnable.class)))
                .thenReturn(false);

        service.syncCoreData(new ProviderOverrides(Provider.API_FOOTBALL, Provider.API_FOOTBALL,
                Provider.API_FOOTBALL, Provider.API_FOOTBALL, null));

        verifyNoInteractions(leagues, seasons, teams, fixtures, footballDataUk);
    }
}
