package com.mmo.module.fb.ingest.application.sync;

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

class FootballDataSyncUseCaseTest {

    private IngestionDistributedLock lock;
    private AppProperties properties;
    private LeagueIngestionService leagues;
    private SeasonIngestionService seasons;
    private TeamIngestionService teams;
    private FixtureIngestionService fixtures;
    private StandingsIngestionService standings;
    private OddsIngestionService odds;
    private FootballDataUkIngestionPort uk;
    private FootballDataSyncUseCase useCase;

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
        useCase = new FootballDataSyncUseCase(lock, properties,
                leagues, seasons, teams, fixtures, standings, odds, uk);
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
        verify(odds).syncUpcomingOdds(any());
        verify(uk).syncCurrentSeasonData();
    }

    @Test
    void refreshesSeasonMappingsForEverySelectedCoreProvider() {
        properties.getIngestion().getProviders().setLeague(com.mmo.module.fb.ingest.enums.Provider.SOFA_SCORE);
        properties.getIngestion().getProviders().setSeason(com.mmo.module.fb.ingest.enums.Provider.UNDER_STATS);
        properties.getIngestion().getProviders().setTeam(com.mmo.module.fb.ingest.enums.Provider.API_FOOTBALL);
        properties.getIngestion().getProviders().setFixture(com.mmo.module.fb.ingest.enums.Provider.SOFA_SCORE);
        properties.getIngestion().getProviders().setStandings(com.mmo.module.fb.ingest.enums.Provider.FOOTBALL_DATA);

        useCase.sync(null, null);

        verify(seasons).syncSeasonsForProvider(com.mmo.module.fb.ingest.enums.Provider.SOFA_SCORE);
        verify(seasons).syncSeasonsForProvider(com.mmo.module.fb.ingest.enums.Provider.UNDER_STATS);
        verify(seasons).syncSeasonsForProvider(com.mmo.module.fb.ingest.enums.Provider.API_FOOTBALL);
        verify(seasons).syncSeasonsForProvider(com.mmo.module.fb.ingest.enums.Provider.FOOTBALL_DATA);
    }

    @Test
    void skipsAllStagesWhenGlobalLockIsBusy() {
        doReturn(false).when(lock).execute(eq(IngestionDistributedLock.GLOBAL_INGEST_LOCK), any(Runnable.class));

        useCase.sync(null, null);

        verifyNoInteractions(leagues, seasons, teams, fixtures, standings, odds, uk);
    }
}
