package com.mmo.module.fb.ingest.application.orchestration;

import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.application.lock.IngestionDistributedLock;
import com.mmo.module.fb.ingest.application.model.ProviderOverrides;
import com.mmo.module.fb.ingest.application.port.in.FixtureIngestionUseCase;
import com.mmo.module.fb.ingest.application.port.in.LeagueIngestionUseCase;
import com.mmo.module.fb.ingest.application.port.in.SeasonIngestionUseCase;
import com.mmo.module.fb.ingest.application.port.in.StandingsIngestionUseCase;
import com.mmo.module.fb.ingest.application.port.in.TeamIngestionUseCase;
import com.mmo.module.fb.ingest.application.port.out.ProviderCheckpointPort;
import com.mmo.module.fb.ingest.application.port.out.provider.SeasonBatchIngestionPort;
import com.mmo.module.fb.ingest.application.registry.SeasonBatchIngestionRegistry;
import com.mmo.module.fb.ingest.enums.CheckpointStatus;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.Dataset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CoreDataBackfillOrchestratorTest {

    private IngestionDistributedLock lock;
    private AppProperties properties;
    private ProviderCheckpointPort checkpoints;
    private IngestionPlanFactory planFactory;
    private SeasonBatchIngestionPort footballDataUk;
    private SeasonBatchIngestionRegistry seasonBatchRegistry;
    private LeagueIngestionUseCase leagues;
    private SeasonIngestionUseCase seasons;
    private TeamIngestionUseCase teams;
    private FixtureIngestionUseCase fixtures;
    private StandingsIngestionUseCase standings;
    private CoreDataBackfillOrchestrator service;

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

        checkpoints = mock(ProviderCheckpointPort.class);
        planFactory = mock(IngestionPlanFactory.class);
        footballDataUk = mock(SeasonBatchIngestionPort.class);
        when(footballDataUk.getProvider()).thenReturn(Provider.FOOTBALL_DATA_UK);
        when(footballDataUk.supportedDatasets()).thenReturn(java.util.Set.of(
                Dataset.SEASON, Dataset.TEAM, Dataset.FIXTURE, Dataset.MATCH_STATISTICS,
                Dataset.MATCH_XG, Dataset.HISTORICAL_ODDS));
        seasonBatchRegistry = new SeasonBatchIngestionRegistry(List.of(footballDataUk));
        clearInvocations(footballDataUk);
        leagues = mock(LeagueIngestionUseCase.class);
        seasons = mock(SeasonIngestionUseCase.class);
        teams = mock(TeamIngestionUseCase.class);
        fixtures = mock(FixtureIngestionUseCase.class);
        standings = mock(StandingsIngestionUseCase.class);
        when(planFactory.historicalPlan()).thenReturn(historicalFootballDataPlan());

        service = new CoreDataBackfillOrchestrator(lock, properties, checkpoints, planFactory,
                seasonBatchRegistry, leagues, seasons, teams, fixtures, standings);
    }

    @Test
    void recordsIndependentTeamAndFixtureCheckpointOutcomes() {
        doThrow(new IllegalStateException("fixture provider unavailable"))
                .when(fixtures).syncSeasonForProvider(Provider.FOOTBALL_DATA, 20L, null, null);

        assertThrows(IllegalStateException.class, () -> service.syncCoreData(
                new ProviderOverrides(Provider.FOOTBALL_DATA, Provider.FOOTBALL_DATA,
                        Provider.FOOTBALL_DATA, Provider.FOOTBALL_DATA, null)));

        verify(checkpoints).markStatus(Provider.FOOTBALL_DATA, Dataset.TEAM,
                39L, "2024/2025", CheckpointStatus.COMPLETED);
        verify(checkpoints).markStatus(Provider.FOOTBALL_DATA, Dataset.FIXTURE,
                39L, "2024/2025", CheckpointStatus.FAILED);
        verify(teams).syncSeasonForProvider(Provider.FOOTBALL_DATA, 20L);
        verify(fixtures).syncSeasonForProvider(Provider.FOOTBALL_DATA, 20L, null, null);
        verify(footballDataUk, never()).prepareHistoricalSeasons();
    }

    @Test
    void continuesUkBackfillWhenFootballDataFixtureStageFails() {
        AppProperties.ProviderConfig ukConfig = new AppProperties.ProviderConfig();
        ukConfig.setEnabled(true);
        properties.getIngestion().getProviderConfig().put(Provider.FOOTBALL_DATA_UK, ukConfig);
        doThrow(new IllegalStateException("fixture provider unavailable"))
                .when(fixtures).syncSeasonForProvider(Provider.FOOTBALL_DATA, 20L, null, null);
        when(planFactory.historicalPlan()).thenReturn(historicalFootballDataAndUkPlan());

        assertThrows(IllegalStateException.class, () -> service.syncCoreData(ProviderOverrides.defaults()));

        verify(footballDataUk).prepareHistoricalSeasons();
        verify(footballDataUk).syncCoreSeason(20L, true);
        verify(checkpoints).markCompleted(Provider.FOOTBALL_DATA_UK, "CORE_INITIAL_BACKFILL");
    }

    @Test
    void completedScopedCheckpointsSkipHistoricalResourceFetches() {
        when(checkpoints.isSatisfied(Provider.FOOTBALL_DATA, Dataset.TEAM,
                39L, "2024/2025")).thenReturn(true);
        when(checkpoints.isSatisfied(Provider.FOOTBALL_DATA, Dataset.FIXTURE,
                39L, "2024/2025")).thenReturn(true);

        service.syncCoreData(ProviderOverrides.defaults());

        verify(leagues).syncLeaguesForProvider(Provider.FOOTBALL_DATA);
        verify(seasons).syncSeasonsForProvider(Provider.FOOTBALL_DATA);
        verifyNoInteractions(teams, fixtures);
        verify(footballDataUk, never()).prepareHistoricalSeasons();
        verify(footballDataUk, never()).syncCoreSeason(anyLong(), anyBoolean());
    }

    @Test
    void providerOverridesCannotBypassSeasonAwarePolicy() {
        service.syncCoreData(new ProviderOverrides(Provider.FOOTBALL_DATA, Provider.SOFA_SCORE,
                Provider.SOFA_SCORE, Provider.SOFA_SCORE, Provider.SOFA_SCORE));

        verify(teams).syncSeasonForProvider(Provider.FOOTBALL_DATA, 20L);
        verify(fixtures).syncSeasonForProvider(Provider.FOOTBALL_DATA, 20L, null, null);
    }

    @Test
    void revisitsUkBackfillEvenWhenLegacyGlobalCheckpointExists() {
        AppProperties.ProviderConfig ukConfig = new AppProperties.ProviderConfig();
        ukConfig.setEnabled(true);
        properties.getIngestion().getProviderConfig().put(Provider.FOOTBALL_DATA_UK, ukConfig);
        when(checkpoints.isCompleted(Provider.FOOTBALL_DATA_UK, "CORE_INITIAL_BACKFILL"))
                .thenReturn(true);
        when(planFactory.historicalPlan()).thenReturn(historicalFootballDataAndUkPlan());

        service.syncCoreData(ProviderOverrides.defaults());

        verify(footballDataUk).prepareHistoricalSeasons();
        verify(footballDataUk).syncCoreSeason(20L, true);
        verify(checkpoints).markCompleted(Provider.FOOTBALL_DATA_UK, "CORE_INITIAL_BACKFILL");
    }

    @Test
    void doesNotStartWorkflowWhenGlobalLockIsBusy() {
        doReturn(false).when(lock).execute(eq(IngestionDistributedLock.GLOBAL_INGEST_LOCK), any(Runnable.class));

        service.syncCoreData(ProviderOverrides.defaults());

        verifyNoInteractions(planFactory, checkpoints, leagues, seasons, teams, fixtures, standings);
    }

    private IngestionPlan historicalFootballDataAndUkPlan() {
        return new IngestionPlan(List.of(
                new IngestionWorkUnit(Dataset.SEASON, 20L, 39L, "2024/2025",
                        Provider.FOOTBALL_DATA, IngestionWorkUnit.Role.PRIMARY),
                new IngestionWorkUnit(Dataset.TEAM, 20L, 39L, "2024/2025",
                        Provider.FOOTBALL_DATA, IngestionWorkUnit.Role.PRIMARY),
                new IngestionWorkUnit(Dataset.FIXTURE, 20L, 39L, "2024/2025",
                        Provider.FOOTBALL_DATA, IngestionWorkUnit.Role.PRIMARY),
                new IngestionWorkUnit(Dataset.TEAM, 20L, 39L, "2024/2025",
                        Provider.FOOTBALL_DATA_UK, IngestionWorkUnit.Role.PRIMARY),
                new IngestionWorkUnit(Dataset.FIXTURE, 20L, 39L, "2024/2025",
                        Provider.FOOTBALL_DATA_UK, IngestionWorkUnit.Role.PRIMARY)
        ));
    }

    private IngestionPlan historicalFootballDataPlan() {
        return new IngestionPlan(List.of(
                new IngestionWorkUnit(Dataset.SEASON, 20L, 39L, "2024/2025",
                        Provider.FOOTBALL_DATA, IngestionWorkUnit.Role.PRIMARY),
                new IngestionWorkUnit(Dataset.TEAM, 20L, 39L, "2024/2025",
                        Provider.FOOTBALL_DATA, IngestionWorkUnit.Role.PRIMARY),
                new IngestionWorkUnit(Dataset.FIXTURE, 20L, 39L, "2024/2025",
                        Provider.FOOTBALL_DATA, IngestionWorkUnit.Role.PRIMARY)
        ));
    }
}
