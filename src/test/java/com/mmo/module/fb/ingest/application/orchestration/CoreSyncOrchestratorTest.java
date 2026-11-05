package com.mmo.module.fb.ingest.application.orchestration;

import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.application.lock.IngestionDistributedLock;
import com.mmo.module.fb.ingest.application.port.out.FootballDataUkIngestionPort;
import com.mmo.module.fb.ingest.application.port.out.ProviderCheckpointStore;
import com.mmo.module.fb.ingest.application.exception.IngestionPartialException;
import com.mmo.module.fb.ingest.enums.CheckpointStatus;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.Dataset;
import com.mmo.module.fb.ingest.service.FixtureIngestionService;
import com.mmo.module.fb.ingest.service.LeagueIngestionService;
import com.mmo.module.fb.ingest.service.SeasonIngestionService;
import com.mmo.module.fb.ingest.service.StandingsIngestionService;
import com.mmo.module.fb.ingest.service.TeamIngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CoreSyncOrchestratorTest {

    private IngestionDistributedLock lock;
    private AppProperties properties;
    private IngestionPlanFactory planFactory;
    private ProviderCheckpointStore checkpoints;
    private LeagueIngestionService leagues;
    private SeasonIngestionService seasons;
    private TeamIngestionService teams;
    private FixtureIngestionService fixtures;
    private StandingsIngestionService standings;
    private FootballDataUkIngestionPort uk;
    private CoreSyncOrchestrator useCase;

    @BeforeEach
    void setUp() {
        lock = mock(IngestionDistributedLock.class);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return true;
        }).when(lock).execute(anyString(), any(Runnable.class));
        properties = new AppProperties();
        properties.getIngestion().getProviders().setLeague(Provider.FOOTBALL_DATA);
        planFactory = mock(IngestionPlanFactory.class);
        checkpoints = mock(ProviderCheckpointStore.class);
        leagues = mock(LeagueIngestionService.class);
        seasons = mock(SeasonIngestionService.class);
        teams = mock(TeamIngestionService.class);
        fixtures = mock(FixtureIngestionService.class);
        standings = mock(StandingsIngestionService.class);
        uk = mock(FootballDataUkIngestionPort.class);
        when(planFactory.activeSeasonPlan()).thenReturn(modernSeasonPlan());
        useCase = new CoreSyncOrchestrator(lock, properties, planFactory, checkpoints,
                leagues, seasons, teams, fixtures, standings, uk);
    }

    @Test
    void executesResourcesFromDatasetSeasonPlanAndPersistsScopedCheckpoints() {
        useCase.sync(null, null);

        verify(lock).execute(eq(IngestionDistributedLock.GLOBAL_INGEST_LOCK), any(Runnable.class));
        verify(planFactory).activeSeasonPlan();
        verify(leagues).syncLeaguesForProvider(Provider.FOOTBALL_DATA);
        verify(seasons).syncSeasonsForProvider(Provider.FOOTBALL_DATA);
        verify(checkpoints).markStatus(Provider.FOOTBALL_DATA, Dataset.SEASON,
                39L, "2025/2026", CheckpointStatus.COMPLETED);
        verify(teams).syncSeasonForProvider(Provider.FOOTBALL_DATA, 100L);
        verify(fixtures).syncSeasonForProvider(Provider.FOOTBALL_DATA, 100L, null, null);
        verify(standings).syncSeasonForProvider(Provider.FOOTBALL_DATA, 100L);
        verify(uk).syncActiveSeasonsData();
        verify(checkpoints).markStatus(Provider.FOOTBALL_DATA, Dataset.TEAM,
                39L, "2025/2026", CheckpointStatus.RUNNING);
        verify(checkpoints).markStatus(Provider.FOOTBALL_DATA, Dataset.TEAM,
                39L, "2025/2026", CheckpointStatus.COMPLETED);
        verify(checkpoints).markStatus(Provider.FOOTBALL_DATA, Dataset.FIXTURE,
                39L, "2025/2026", CheckpointStatus.COMPLETED);
    }

    @Test
    void skipsAllProviderStagesWhenNoSupportedSeasonRoutesExist() {
        when(planFactory.activeSeasonPlan()).thenReturn(new IngestionPlan(List.of()));

        useCase.sync(null, null);

        verifyNoInteractions(leagues, seasons, teams, fixtures, standings, uk, checkpoints);
    }

    @Test
    void recordsFailedScopedCheckpointAndContinuesIndependentDatasetStages() {
        doThrow(new IllegalStateException("provider down"))
                .when(teams).syncSeasonForProvider(Provider.FOOTBALL_DATA, 100L);

        assertThrows(IllegalStateException.class, () -> useCase.sync(null, null));

        verify(checkpoints).markStatus(Provider.FOOTBALL_DATA, Dataset.TEAM,
                39L, "2025/2026", CheckpointStatus.FAILED);
        verify(fixtures).syncSeasonForProvider(Provider.FOOTBALL_DATA, 100L, null, null);
        verify(checkpoints).markStatus(Provider.FOOTBALL_DATA, Dataset.FIXTURE,
                39L, "2025/2026", CheckpointStatus.COMPLETED);
        verify(uk).syncActiveSeasonsData();
    }

    @Test
    void recordsPartialCheckpointWhenStandingsProviderIsRateLimited() {
        doThrow(new IngestionPartialException("rate limited"))
                .when(standings).syncSeasonForProvider(Provider.FOOTBALL_DATA, 100L);

        assertThrows(IllegalStateException.class, () -> useCase.sync(null, null));

        verify(checkpoints).markStatus(Provider.FOOTBALL_DATA, Dataset.STANDINGS,
                39L, "2025/2026", CheckpointStatus.PARTIAL);
        verify(uk).syncActiveSeasonsData();
    }

    @Test
    void neverRunsUpcomingOddsEvenWhenAnOverrideIsSupplied() {
        useCase.sync(null, Provider.API_FOOTBALL);

        verify(uk).syncActiveSeasonsData();
        verify(checkpoints, never()).markStatus(any(), eq(Dataset.UPCOMING_ODDS), anyLong(), anyString(), any());
    }

    @Test
    void skipsAllStagesWhenGlobalLockIsBusy() {
        doReturn(false).when(lock).execute(eq(IngestionDistributedLock.GLOBAL_INGEST_LOCK), any(Runnable.class));

        useCase.sync(null, null);

        verifyNoInteractions(planFactory, checkpoints, leagues, seasons, teams, fixtures, standings, uk);
    }

    private IngestionPlan modernSeasonPlan() {
        return new IngestionPlan(List.of(
                unit(Dataset.SEASON, Provider.FOOTBALL_DATA, IngestionWorkUnit.Role.PRIMARY),
                unit(Dataset.TEAM, Provider.FOOTBALL_DATA, IngestionWorkUnit.Role.PRIMARY),
                unit(Dataset.FIXTURE, Provider.FOOTBALL_DATA, IngestionWorkUnit.Role.PRIMARY),
                unit(Dataset.FIXTURE, Provider.FOOTBALL_DATA_UK, IngestionWorkUnit.Role.SECONDARY),
                unit(Dataset.STANDINGS, Provider.FOOTBALL_DATA, IngestionWorkUnit.Role.PRIMARY),
                unit(Dataset.MATCH_STATISTICS, Provider.FOOTBALL_DATA_UK, IngestionWorkUnit.Role.PRIMARY),
                unit(Dataset.MATCH_XG, Provider.FOOTBALL_DATA_UK, IngestionWorkUnit.Role.PRIMARY),
                unit(Dataset.HISTORICAL_ODDS, Provider.FOOTBALL_DATA_UK, IngestionWorkUnit.Role.PRIMARY)
        ));
    }

    private IngestionWorkUnit unit(Dataset dataset, Provider provider, IngestionWorkUnit.Role role) {
        return new IngestionWorkUnit(dataset, 100L, 39L, "2025/2026", provider, role);
    }
}
