package com.mmo.module.fb.ingest.application.orchestration;

import com.mmo.module.fb.ingest.application.lock.IngestionDistributedLock;
import com.mmo.module.fb.ingest.application.model.ProviderOverrides;
import com.mmo.module.fb.ingest.application.port.in.ActiveSeasonDataSyncUseCase;
import com.mmo.module.fb.ingest.application.port.in.CoreDataBackfillUseCase;
import com.mmo.module.fb.ingest.application.port.in.MatchEnrichmentUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FullDataRebuildOrchestratorTest {

    private IngestionDistributedLock lock;
    private CoreDataBackfillUseCase core;
    private ActiveSeasonDataSyncUseCase active;
    private MatchEnrichmentUseCase enrichment;
    private FullDataRebuildOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        lock = mock(IngestionDistributedLock.class);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return true;
        }).when(lock).executeScoped(anyString(), any(Runnable.class));
        core = mock(CoreDataBackfillUseCase.class);
        active = mock(ActiveSeasonDataSyncUseCase.class);
        enrichment = mock(MatchEnrichmentUseCase.class);
        orchestrator = new FullDataRebuildOrchestrator(lock, core, active, enrichment);
    }

    @Test
    void executesExistingWorkflowsInDependencyOrder() {
        orchestrator.rebuildAllData();

        InOrder order = inOrder(core, active, enrichment);
        order.verify(core).syncCoreData();
        order.verify(active).sync(ProviderOverrides.defaults(), null);
        order.verify(enrichment).enrichFinishedMatches();
    }

    @Test
    void abortsDownstreamPhasesWhenHistoricalCoreFails() {
        doThrow(new IllegalStateException("provider unavailable")).when(core).syncCoreData();

        assertThrows(IllegalStateException.class, () -> orchestrator.rebuildAllData());

        verifyNoInteractions(active, enrichment);
    }

    @Test
    void abortsEnrichmentWhenActiveCoreFails() {
        doThrow(new IllegalStateException("active sync incomplete"))
                .when(active).sync(any(ProviderOverrides.class), isNull());

        assertThrows(IllegalStateException.class, () -> orchestrator.rebuildAllData());

        verify(core).syncCoreData();
        verify(active).sync(ProviderOverrides.defaults(), null);
        verifyNoInteractions(enrichment);
    }

    @Test
    void failsFastWhenAnotherFullRebuildOwnsTheLock() {
        doReturn(false).when(lock).executeScoped(eq("workflow/full-data-rebuild"), any(Runnable.class));

        assertThrows(IllegalStateException.class, () -> orchestrator.rebuildAllData());

        verifyNoInteractions(core, active, enrichment);
    }
}
