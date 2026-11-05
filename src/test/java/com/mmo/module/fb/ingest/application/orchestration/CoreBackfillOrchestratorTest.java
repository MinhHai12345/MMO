package com.mmo.module.fb.ingest.application.orchestration;

import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.application.lock.IngestionDistributedLock;
import com.mmo.module.fb.ingest.application.port.out.FootballDataUkIngestionPort;
import com.mmo.module.fb.ingest.application.port.out.ProviderCheckpointStore;
import com.mmo.module.fb.ingest.enums.CheckpointStatus;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.Dataset;
import com.mmo.module.fb.ingest.service.FixtureIngestionService;
import com.mmo.module.fb.ingest.service.LeagueIngestionService;
import com.mmo.module.fb.ingest.service.ProviderOverrides;
import com.mmo.module.fb.ingest.service.SeasonIngestionService;
import com.mmo.module.fb.ingest.service.StandingsIngestionService;
import com.mmo.module.fb.ingest.service.TeamIngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CoreBackfillOrchestratorTest {

    private IngestionDistributedLock lock;
    private AppProperties properties;
    private ProviderCheckpointStore checkpoints;
    private IngestionPlanFactory planFactory;
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
        properties.getIngestion().getProviders().setLeague(Provider.FOOTBALL_DATA);

        checkpoints = mock(ProviderCheckpointStore.class);
        planFactory = mock(IngestionPlanFactory.class);
        footballDataUk = mock(FootballDataUkIngestionPort.class);
        leagues = mock(LeagueIngestionService.class);
        seasons = mock(SeasonIngestionService.class);
        teams = mock(TeamIngestionService.class);
        fixtures = mock(FixtureIngestionService.class);
        standings = mock(StandingsIngestionService.class);
        when(planFactory.historicalPlan()).thenReturn(historicalFootballDataPlan());

        service = new CoreBackfillOrchestrator(lock, properties, checkpoints, planFactory,
                footballDataUk, leagues, seasons, teams, fixtures, standings);
    }

    @Test
    void recordsIndependentTeamAndFixtureCheckpointOutcomes() {
        doThrow(new IllegalStateException("fixture provider unavailable"))
                .when(fixtures).syncAllSeasonsForProvider(Provider.FOOTBALL_DATA, null, null);

        assertThrows(IllegalStateException.class, () -> service.syncCoreData(
                new ProviderOverrides(Provider.FOOTBALL_DATA, Provider.FOOTBALL_DATA,
                        Provider.FOOTBALL_DATA, Provider.FOOTBALL_DATA, null)));

        verify(checkpoints).markStatus(Provider.FOOTBALL_DATA, Dataset.TEAM,
                39L, "2024/2025", CheckpointStatus.COMPLETED);
        verify(checkpoints).markStatus(Provider.FOOTBALL_DATA, Dataset.FIXTURE,
                39L, "2024/2025", CheckpointStatus.FAILED);
        verify(teams).syncAllSeasonsForProvider(Provider.FOOTBALL_DATA);
        verify(fixtures).syncAllSeasonsForProvider(Provider.FOOTBALL_DATA, null, null);
        verify(footballDataUk, never()).syncInitialData();
    }

    @Test
    void completedScopedCheckpointsSkipHistoricalResourceFetches() {
        when(checkpoints.isCompleted(Provider.FOOTBALL_DATA, Dataset.TEAM,
                39L, "2024/2025")).thenReturn(true);
        when(checkpoints.isCompleted(Provider.FOOTBALL_DATA, Dataset.FIXTURE,
                39L, "2024/2025")).thenReturn(true);

        service.syncCoreData(ProviderOverrides.defaults());

        verify(leagues).syncLeaguesForProvider(Provider.FOOTBALL_DATA);
        verify(seasons).syncSeasonsForProvider(Provider.FOOTBALL_DATA);
        verifyNoInteractions(teams, fixtures, footballDataUk);
    }

    @Test
    void providerOverridesCannotBypassSeasonAwarePolicy() {
        service.syncCoreData(new ProviderOverrides(Provider.FOOTBALL_DATA, Provider.SOFA_SCORE,
                Provider.SOFA_SCORE, Provider.SOFA_SCORE, Provider.SOFA_SCORE));

        verify(teams).syncAllSeasonsForProvider(Provider.FOOTBALL_DATA);
        verify(fixtures).syncAllSeasonsForProvider(Provider.FOOTBALL_DATA, null, null);
    }

    @Test
    void doesNotStartWorkflowWhenGlobalLockIsBusy() {
        doReturn(false).when(lock).execute(eq(IngestionDistributedLock.GLOBAL_INGEST_LOCK), any(Runnable.class));

        service.syncCoreData(ProviderOverrides.defaults());

        verifyNoInteractions(planFactory, checkpoints, footballDataUk, leagues, seasons, teams, fixtures, standings);
    }

    private IngestionPlan historicalFootballDataPlan() {
        return new IngestionPlan(List.of(
                new IngestionWorkUnit(Dataset.TEAM, 20L, 39L, "2024/2025",
                        Provider.FOOTBALL_DATA, IngestionWorkUnit.Role.PRIMARY),
                new IngestionWorkUnit(Dataset.FIXTURE, 20L, 39L, "2024/2025",
                        Provider.FOOTBALL_DATA, IngestionWorkUnit.Role.PRIMARY)
        ));
    }
}
