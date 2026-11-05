package com.mmo.module.fb.ingest.application.enrichment;

import com.mmo.module.fb.ingest.application.lock.IngestionDistributedLock;
import com.mmo.module.fb.ingest.application.port.out.FootballDataUkIngestionPort;
import com.mmo.module.fb.ingest.application.port.out.ProviderCheckpointStore;
import com.mmo.module.fb.ingest.enums.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MatchEnrichmentUseCaseTest {

    private IngestionDistributedLock lock;
    private ProviderCheckpointStore checkpoints;
    private FootballDataUkIngestionPort footballDataUk;
    private MatchEnrichmentUseCase useCase;

    @BeforeEach
    void setUp() {
        lock = mock(IngestionDistributedLock.class);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return true;
        }).when(lock).execute(anyString(), any(Runnable.class));
        checkpoints = mock(ProviderCheckpointStore.class);
        when(checkpoints.isCompleted(any(), anyString())).thenReturn(false);
        footballDataUk = mock(FootballDataUkIngestionPort.class);
        useCase = new MatchEnrichmentUseCase(lock, checkpoints, footballDataUk);
    }

    @Test
    void skipsAllEnrichmentUntilFootballDataUkCoreBackfillIsComplete() {
        useCase.enrichFinishedMatches();

        verify(lock).execute(eq(IngestionDistributedLock.GLOBAL_INGEST_LOCK), any(Runnable.class));
        verify(footballDataUk, never()).syncInitialEnrichment();
        verify(footballDataUk, never()).syncActiveSeasonsEnrichment();
    }

    @Test
    void performsHistoricalFootballDataUkEnrichmentOnceCoreBackfillIsComplete() {
        when(checkpoints.isCompleted(Provider.FOOTBALL_DATA_UK, "CORE_INITIAL_BACKFILL"))
                .thenReturn(true);
        when(footballDataUk.syncInitialEnrichment()).thenReturn(true);

        useCase.enrichFinishedMatches();

        verify(footballDataUk).syncInitialEnrichment();
        verify(footballDataUk, never()).syncActiveSeasonsEnrichment();
        verify(checkpoints).markCompleted(Provider.FOOTBALL_DATA_UK, "MATCH_ENRICHMENT_INITIAL");
    }

    @Test
    void refreshesOnlyActiveSeasonsAfterHistoricalFootballDataUkEnrichmentIsComplete() {
        when(checkpoints.isCompleted(Provider.FOOTBALL_DATA_UK, "CORE_INITIAL_BACKFILL"))
                .thenReturn(true);
        when(checkpoints.isCompleted(Provider.FOOTBALL_DATA_UK, "MATCH_ENRICHMENT_INITIAL"))
                .thenReturn(true);

        useCase.enrichFinishedMatches();

        verify(footballDataUk, never()).syncInitialEnrichment();
        verify(footballDataUk).syncActiveSeasonsEnrichment();
    }

    @Test
    void skipsEnrichmentWhenGlobalLockIsBusy() {
        doReturn(false).when(lock).execute(eq(IngestionDistributedLock.GLOBAL_INGEST_LOCK), any(Runnable.class));

        useCase.enrichFinishedMatches();

        verifyNoInteractions(checkpoints, footballDataUk);
    }
}
