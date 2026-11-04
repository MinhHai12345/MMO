package com.mmo.module.fb.ingest.application.enrichment;

import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.application.lock.IngestionDistributedLock;
import com.mmo.module.fb.ingest.application.port.out.FootballDataUkIngestionPort;
import com.mmo.module.fb.ingest.application.port.out.HistoricalStatisticsIngestionPort;
import com.mmo.module.fb.ingest.application.port.out.MatchStatisticsIngestionPort;
import com.mmo.module.fb.ingest.application.port.out.PlayerStatisticsIngestionPort;
import com.mmo.module.fb.ingest.application.port.out.ProviderCheckpointStore;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.service.SofaScoreBootstrapService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MatchEnrichmentUseCaseTest {

    private IngestionDistributedLock lock;
    private AppProperties properties;
    private ProviderCheckpointStore checkpoints;
    private FootballDataUkIngestionPort footballDataUk;
    private HistoricalStatisticsIngestionPort understat;
    private SofaScoreBootstrapService sofaBootstrap;
    private MatchStatisticsIngestionPort matchStats;
    private PlayerStatisticsIngestionPort playerStats;
    private MatchEnrichmentUseCase useCase;

    @BeforeEach
    void setUp() {
        lock = mock(IngestionDistributedLock.class);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return true;
        }).when(lock).execute(anyString(), any(Runnable.class));
        properties = new AppProperties();
        properties.getIngestion().getProviders().setHistoricalStats(Provider.UNDER_STATS);
        checkpoints = mock(ProviderCheckpointStore.class);
        when(checkpoints.isCompleted(any(), anyString())).thenReturn(false);
        footballDataUk = mock(FootballDataUkIngestionPort.class);
        understat = mock(HistoricalStatisticsIngestionPort.class);
        sofaBootstrap = mock(SofaScoreBootstrapService.class);
        matchStats = mock(MatchStatisticsIngestionPort.class);
        playerStats = mock(PlayerStatisticsIngestionPort.class);
        useCase = new MatchEnrichmentUseCase(lock, properties, checkpoints,
                footballDataUk, understat, sofaBootstrap, matchStats, playerStats);
    }

    @Test
    void enrichesThroughConfiguredProviderUnderGlobalLock() {
        useCase.enrichFinishedMatches();

        verify(lock).execute(eq(IngestionDistributedLock.GLOBAL_INGEST_LOCK), any(Runnable.class));
        verify(understat).syncActiveSeasons();
        verify(playerStats).syncFinishedMatchPlayerStatistics();
        verify(footballDataUk, never()).syncInitialEnrichment();
        verify(matchStats, never()).storeMatchStatistics();
    }

    @Test
    void skipsEnrichmentWhenGlobalLockIsBusy() {
        doReturn(false).when(lock).execute(eq(IngestionDistributedLock.GLOBAL_INGEST_LOCK), any(Runnable.class));

        useCase.enrichFinishedMatches();

        verifyNoInteractions(checkpoints, footballDataUk, understat, sofaBootstrap, matchStats, playerStats);
    }
}
