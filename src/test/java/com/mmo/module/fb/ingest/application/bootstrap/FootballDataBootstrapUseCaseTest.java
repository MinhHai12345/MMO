package com.mmo.module.fb.ingest.application.bootstrap;

import com.mmo.module.fb.ingest.application.orchestration.CoreBackfillOrchestrator;

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

class CoreBackfillOrchestratorTest {

    private IngestionDistributedLock lock;
    private AppProperties properties;
    private ProviderCheckpointStore checkpoints;
    private FootballDataUkIngestionPort footballDataUk;
    private LeagueIngestionService leagues;
    private SeasonIngestionService seasons;
    private TeamIngestionService teams;
    private FixtureIngestionService fixtures;
    private StandingsIngestionService standings;
    private CoreBackfillOrchestrator service;

    @BeforeEach
    void setUp() {
        lock = mock(IngestionDistributedLock.class);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return true;
        }).when(lock).execute(anyString(), any(Runnable.class));

        properties = new AppProperties();
        AppProperties.ProviderConfig footballDataConfig = new AppProperties.ProviderConfig();
        footballDataConfig.setEnabled(true);
        footballDataConfig.setApiKey("test-api-key");
        properties.getIngestion().getProviderConfig().put(Provider.FOOTBALL_DATA, footballDataConfig);
        AppProperties.ProviderConfig ukConfig = new AppProperties.ProviderConfig();
        ukConfig.setEnabled(false);
        properties.getIngestion().getProviderConfig().put(Provider.FOOTBALL_DATA_UK, ukConfig);
        properties.getIngestion().getProviders().setLeague(Provider.FOOTBALL_DATA);
        properties.getIngestion().getProviders().setSeason(Provider.FOOTBALL_DATA);
        properties.getIngestion().getProviders().setTeam(Provider.FOOTBALL_DATA);
        properties.getIngestion().getProviders().setFixture(Provider.FOOTBALL_DATA);

        checkpoints = mock(ProviderCheckpointStore.class);
        footballDataUk = mock(FootballDataUkIngestionPort.class);
        leagues = mock(LeagueIngestionService.class);
        seasons = mock(SeasonIngestionService.class);
        teams = mock(TeamIngestionService.class);
        fixtures = mock(FixtureIngestionService.class);
        standings = mock(StandingsIngestionService.class);

        when(checkpoints.isCompleted(any(), anyString())).thenReturn(false);

        service = new CoreBackfillOrchestrator(
                lock, properties, checkpoints, footballDataUk,
                leagues, seasons, teams, fixtures, standings);
    }

    @Test
    void recordsTeamCheckpointButNotFixtureCheckpointWhenFixtureBackfillFails() {
        doThrow(new IllegalStateException("fixture provider unavailable"))
                .when(fixtures).syncAllSeasonsForProvider(Provider.FOOTBALL_DATA, null, null);

        assertThrows(IllegalStateException.class, () -> service.syncCoreData(
                new ProviderOverrides(Provider.FOOTBALL_DATA, Provider.FOOTBALL_DATA,
                        Provider.FOOTBALL_DATA, Provider.FOOTBALL_DATA, null)));

        verify(checkpoints).markCompleted(Provider.FOOTBALL_DATA, "CORE_BACKFILL_TEAMS");
        verify(checkpoints, never()).markCompleted(Provider.FOOTBALL_DATA, "CORE_BACKFILL_FIXTURES");
        verify(teams).syncAllSeasonsForProvider(Provider.FOOTBALL_DATA);
        verify(fixtures).syncAllSeasonsForProvider(Provider.FOOTBALL_DATA, null, null);
        verify(footballDataUk, never()).syncInitialData();
    }

    @Test
    void completedResourceCheckpointsMakeBootstrapNoOp() {
        when(checkpoints.isCompleted(Provider.FOOTBALL_DATA, "CORE_BACKFILL_TEAMS")).thenReturn(true);
        when(checkpoints.isCompleted(Provider.FOOTBALL_DATA, "CORE_BACKFILL_FIXTURES")).thenReturn(true);

        service.syncCoreData(new ProviderOverrides(Provider.FOOTBALL_DATA, Provider.FOOTBALL_DATA,
                Provider.FOOTBALL_DATA, Provider.FOOTBALL_DATA, null));

        verifyNoInteractions(leagues, seasons, teams, fixtures, footballDataUk);
    }

    @Test
    void rejectsDisabledThirdPartyProvidersDuringBootstrap() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> service.syncCoreData(new ProviderOverrides(
                        Provider.SOFA_SCORE, Provider.UNDER_STATS,
                        Provider.SOFA_SCORE, Provider.UNDER_STATS, null)));

        verifyNoInteractions(leagues, seasons, teams, fixtures, footballDataUk);
    }

    @Test
    void doesNotStartWorkflowWhenGlobalLockIsBusy() {
        doReturn(false).when(lock).execute(eq(IngestionDistributedLock.GLOBAL_INGEST_LOCK), any(Runnable.class));

        service.syncCoreData(new ProviderOverrides(Provider.FOOTBALL_DATA, Provider.FOOTBALL_DATA,
                Provider.FOOTBALL_DATA, Provider.FOOTBALL_DATA, null));

        verifyNoInteractions(leagues, seasons, teams, fixtures, footballDataUk);
    }
}
