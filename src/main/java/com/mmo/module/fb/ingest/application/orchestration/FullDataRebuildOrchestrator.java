package com.mmo.module.fb.ingest.application.orchestration;

import com.mmo.module.fb.ingest.application.lock.IngestionDistributedLock;
import com.mmo.module.fb.ingest.application.model.ProviderOverrides;
import com.mmo.module.fb.ingest.application.port.in.ActiveSeasonDataSyncUseCase;
import com.mmo.module.fb.ingest.application.port.in.CoreDataBackfillUseCase;
import com.mmo.module.fb.ingest.application.port.in.FullDataRebuildUseCase;
import com.mmo.module.fb.ingest.application.port.in.MatchEnrichmentUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Coordinates a complete non-destructive data rebuild using the existing workflows.
 *
 * <p>Ordering is intentional: historical catalogue/core first, then active-season core
 * refresh, then match enrichment. A failed phase aborts the pipeline; downstream phases
 * are never started with incomplete prerequisites.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FullDataRebuildOrchestrator implements FullDataRebuildUseCase {

    private static final String WORKFLOW_LOCK = "workflow/full-data-rebuild";

    private final IngestionDistributedLock distributedLock;
    private final CoreDataBackfillUseCase coreDataBackfill;
    private final ActiveSeasonDataSyncUseCase activeSeasonDataSync;
    private final MatchEnrichmentUseCase matchEnrichment;

    @Override
    public void rebuildAllData() {
        boolean acquired = distributedLock.executeScoped(WORKFLOW_LOCK, this::executePipeline);
        if (!acquired) {
            throw new IllegalStateException("Full data rebuild was not started because another rebuild owns the lock");
        }
    }

    private void executePipeline() {
        long startedAt = System.nanoTime();
        log.info("Full data rebuild STARTED");

        runPhase("historical-core", coreDataBackfill::syncCoreData);
        runPhase("active-season-core",
                () -> activeSeasonDataSync.sync(ProviderOverrides.defaults(), null));
        runPhase("match-enrichment", matchEnrichment::enrichFinishedMatches);

        log.info("Full data rebuild COMPLETED durationMs={}",
                (System.nanoTime() - startedAt) / 1_000_000);
    }

    private void runPhase(String phase, Runnable operation) {
        long startedAt = System.nanoTime();
        log.info("Full data rebuild phase STARTED: {}", phase);
        try {
            operation.run();
            log.info("Full data rebuild phase COMPLETED: {} durationMs={}",
                    phase, (System.nanoTime() - startedAt) / 1_000_000);
        } catch (RuntimeException exception) {
            log.error("Full data rebuild phase FAILED: {} durationMs={} error={}",
                    phase, (System.nanoTime() - startedAt) / 1_000_000,
                    exception.getMessage(), exception);
            throw new IllegalStateException("Full data rebuild stopped at phase " + phase, exception);
        }
    }
}
