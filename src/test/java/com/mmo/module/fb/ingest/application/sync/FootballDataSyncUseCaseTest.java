package com.mmo.module.fb.ingest.application.sync;

import com.mmo.module.fb.ingest.application.orchestration.CoreSyncOrchestrator;

import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.application.lock.IngestionDistributedLock;
import com.mmo.module.fb.ingest.application.port.out.FootballDataUkIngestionPort;
import com.mmo.module.fb.ingest.service.FixtureIngestionService;
import com.mmo.module.fb.ingest.service.LeagueIngestionService;
import com.mmo.module.fb.ingest.service.OddsIngestionService;
import com.mmo.module.fb.ingest.service.SeasonIngestionService;
import com.mmo.module.fb.ingest.service.StandingsIngestionService;
import com.mmo.module.fb.ingest.service.TeamIngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CoreSyncOrchestratorTest {

    private IngestionDistributedLock lock;
    private AppProperties properties;
    private LeagueIngestionService leagues;
    private SeasonIngestionService seasons;
    private TeamIngestionService teams;
    private FixtureIngestionService fixtures;
    private StandingsIngestionService standings;
    private OddsIngestionService odds;
    private FootballDataUkIngestionPort uk;
    private CoreSyncOrchestrator useCase;

    @BeforeEach
    void setUp() {
        lock = mock(IngestionDistributedLock.class);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return true;
        }).when(lock).execute(anyString(), any(Runnable.class));
        leagues = mock(LeagueIngestionService.class);
        seasons = mock(SeasonIngestionService.class);
        teams = mock(TeamIngestionService.class);
        fixtures = mock(FixtureIngestionService.class);
        standings = mock(StandingsIngestionService.class);
        odds = mock(OddsIngestionService.class);
        uk = mock(FootballDataUkIngestionPort.class);
        properties = new AppProperties();
        useCase = new CoreSyncOrchestrator(lock, properties,
                leagues, seasons, teams, fixtures, standings, uk);
    }

    @Test
    void runsIncrementalCoreStagesUnderGlobalLock() {
        useCase.sync(null, null);

        verify(lock).execute(eq(IngestionDistributedLock.GLOBAL_INGEST_LOCK), any(Runnable.class));
        verify(leagues, atLeastOnce()).syncLeaguesForProvider(any());
        verify(seasons).syncSeasonsForProvider(any());
        verify(teams).syncTeamsForProvider(any());
        verify(fixtures).syncFixturesForProvider(any(), isNull(), isNull());
        verify(standings).syncStandingsForProvider(any());
        verifyNoInteractions(odds);
        verify(uk).syncActiveSeasonsData();
    }

    @Test
    void rejectsTemporarilyDisabledProvidersForCoreApiResources() {
        properties.getIngestion().getProviders().setFixture(
                com.mmo.module.fb.ingest.enums.Provider.SOFA_SCORE);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> useCase.sync(null, null));

        verifyNoInteractions(leagues, seasons, teams, fixtures, standings, odds, uk);
    }

    @Test
    void neverRunsUpcomingOddsDuringPhaseOneEvenWhenAnOverrideIsSupplied() {
        AppProperties.ProviderConfig enabledButOutOfScope = new AppProperties.ProviderConfig();
        enabledButOutOfScope.setEnabled(true);
        properties.getIngestion().getProviderConfig().put(
                com.mmo.module.fb.ingest.enums.Provider.API_FOOTBALL, enabledButOutOfScope);

        useCase.sync(null, com.mmo.module.fb.ingest.enums.Provider.API_FOOTBALL);

        verifyNoInteractions(odds);
        verify(uk).syncActiveSeasonsData();
    }

    @Test
    void skipsAllStagesWhenGlobalLockIsBusy() {
        doReturn(false).when(lock).execute(eq(IngestionDistributedLock.GLOBAL_INGEST_LOCK), any(Runnable.class));

        useCase.sync(null, null);

        verifyNoInteractions(leagues, seasons, teams, fixtures, standings, odds, uk);
    }
}
