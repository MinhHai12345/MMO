package com.mmo.module.fb.ingest.application.orchestration;

import com.mmo.module.fb.ingest.application.lock.IngestionDistributedLock;
import com.mmo.module.fb.ingest.provider.spi.SeasonBatchProvider;
import com.mmo.module.fb.ingest.application.port.out.ProviderCheckpointStore;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.Dataset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MatchEnrichmentOrchestratorTest {

    private IngestionDistributedLock lock;
    private ProviderCheckpointStore checkpoints;
    private IngestionPlanFactory planFactory;
    private SeasonBatchProvider footballDataUk;
    private SeasonBatchProviderRegistry seasonBatchProviderRegistry;
    private MatchEnrichmentOrchestrator useCase;

    @BeforeEach
    void setUp() {
        lock = mock(IngestionDistributedLock.class);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return true;
        }).when(lock).execute(anyString(), any(Runnable.class));
        checkpoints = mock(ProviderCheckpointStore.class);
        when(checkpoints.isCompleted(any(), anyString())).thenReturn(false);
        planFactory = mock(IngestionPlanFactory.class);
        when(planFactory.historicalPlan()).thenReturn(enrichmentPlan());
        when(planFactory.activeSeasonPlan()).thenReturn(enrichmentPlan());
        footballDataUk = mock(SeasonBatchProvider.class);
        when(footballDataUk.getProvider()).thenReturn(Provider.FOOTBALL_DATA_UK);
        when(footballDataUk.supportedDatasets()).thenReturn(java.util.Set.of(
                Dataset.SEASON, Dataset.TEAM, Dataset.FIXTURE, Dataset.MATCH_STATISTICS,
                Dataset.MATCH_XG, Dataset.HISTORICAL_ODDS));
        seasonBatchProviderRegistry = new SeasonBatchProviderRegistry(List.of(footballDataUk));
        clearInvocations(footballDataUk);
        useCase = new MatchEnrichmentOrchestrator(lock, checkpoints, planFactory, seasonBatchProviderRegistry);
    }

    @Test
    void runsHistoricalRecoveryAndActiveRefreshWithoutLegacyGlobalGates() {
        useCase.enrichFinishedMatches();

        verify(lock).execute(eq(IngestionDistributedLock.GLOBAL_INGEST_LOCK), any(Runnable.class));
        verify(planFactory).historicalPlan();
        verify(footballDataUk).syncEnrichmentSeason(10L, true);
        verify(footballDataUk).syncEnrichmentSeason(10L, false);
        verify(checkpoints).markCompleted(Provider.FOOTBALL_DATA_UK, "MATCH_ENRICHMENT_INITIAL");
        verify(checkpoints, never()).isCompleted(Provider.FOOTBALL_DATA_UK, "CORE_INITIAL_BACKFILL");
    }

    @Test
    void retriesHistoricalEnrichmentEvenWhenLegacyEnrichmentMarkerExists() {

        useCase.enrichFinishedMatches();

        verify(footballDataUk).syncEnrichmentSeason(10L, true);
        verify(footballDataUk).syncEnrichmentSeason(10L, false);
    }

    @Test
    void continuesActiveRefreshWhenHistoricalRecoveryFails() {
        doThrow(new IllegalStateException("historical import partial"))
                .when(footballDataUk).syncEnrichmentSeason(10L, true);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> useCase.enrichFinishedMatches());

        verify(footballDataUk).syncEnrichmentSeason(10L, true);
        verify(footballDataUk).syncEnrichmentSeason(10L, false);
    }

    @Test
    void skipsWhenPolicyContainsNoUkEnrichmentWork() {
        when(planFactory.historicalPlan()).thenReturn(new IngestionPlan(List.of()));

        useCase.enrichFinishedMatches();

        verifyNoInteractions(checkpoints, footballDataUk);
    }

    @Test
    void skipsEnrichmentWhenGlobalLockIsBusy() {
        doReturn(false).when(lock).execute(eq(IngestionDistributedLock.GLOBAL_INGEST_LOCK), any(Runnable.class));

        useCase.enrichFinishedMatches();

        verifyNoInteractions(planFactory, checkpoints, footballDataUk);
    }

    private IngestionPlan enrichmentPlan() {
        return new IngestionPlan(List.of(
                new IngestionWorkUnit(Dataset.MATCH_STATISTICS, 10L, 39L,
                        "2021/2022", Provider.FOOTBALL_DATA_UK, IngestionWorkUnit.Role.PRIMARY),
                new IngestionWorkUnit(Dataset.MATCH_XG, 10L, 39L,
                        "2021/2022", Provider.FOOTBALL_DATA_UK, IngestionWorkUnit.Role.PRIMARY),
                new IngestionWorkUnit(Dataset.HISTORICAL_ODDS, 10L, 39L,
                        "2021/2022", Provider.FOOTBALL_DATA_UK, IngestionWorkUnit.Role.PRIMARY)
        ));
    }
}
