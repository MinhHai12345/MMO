package com.mmo.module.fb.ingest.application.orchestration;

import com.mmo.module.fb.ingest.application.lock.IngestionDistributedLock;
import com.mmo.module.fb.ingest.application.port.out.FootballDataUkIngestionPort;
import com.mmo.module.fb.ingest.application.port.out.ProviderCheckpointStore;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.Dataset;
import com.mmo.module.fb.ingest.service.MatchEnrichmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Executes historical and active match enrichment only when the season-aware policy
 * assigns one or more enrichment datasets to Football-Data UK.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchEnrichmentOrchestrator implements MatchEnrichmentService {

    private static final String CORE_INITIAL_CHECKPOINT = "CORE_INITIAL_BACKFILL";
    private static final String ENRICHMENT_INITIAL_CHECKPOINT = "MATCH_ENRICHMENT_INITIAL";

    private final IngestionDistributedLock distributedLock;
    private final ProviderCheckpointStore checkpointStore;
    private final IngestionPlanFactory planFactory;
    private final FootballDataUkIngestionPort footballDataUkSyncService;

    @Override
    public void enrichFinishedMatches() {
        boolean executed = distributedLock.execute(IngestionDistributedLock.GLOBAL_INGEST_LOCK,
                this::enrichFinishedMatchesLocked);
        if (!executed) {
            log.warn("Match enrichment skipped because another ingestion workflow is running");
        }
    }

    private void enrichFinishedMatchesLocked() {
        IngestionPlan plan = planFactory.historicalPlan();
        boolean ukEnrichmentRequired = plan.units().stream().anyMatch(unit ->
                unit.provider() == Provider.FOOTBALL_DATA_UK
                        && unit.role() == IngestionWorkUnit.Role.PRIMARY
                        && (unit.dataset() == Dataset.MATCH_STATISTICS
                        || unit.dataset() == Dataset.MATCH_XG
                        || unit.dataset() == Dataset.HISTORICAL_ODDS));
        if (!ukEnrichmentRequired) {
            log.info("Match enrichment skipped: DataSourcePolicy has no historical Football-Data UK work units");
            return;
        }

        log.info("Match enrichment plan: provider={}, workUnits={}",
                Provider.FOOTBALL_DATA_UK, plan.units().stream()
                        .filter(unit -> unit.provider() == Provider.FOOTBALL_DATA_UK
                                && (unit.dataset() == Dataset.MATCH_STATISTICS
                                || unit.dataset() == Dataset.MATCH_XG
                                || unit.dataset() == Dataset.HISTORICAL_ODDS))
                        .count());

        boolean coreReady = checkpointStore.isCompleted(
                Provider.FOOTBALL_DATA_UK, CORE_INITIAL_CHECKPOINT);
        if (!coreReady) {
            log.info("Skipping match enrichment until the 2014+ Football-Data UK Core CSV import is complete");
            return;
        }

        runStage("football-data-uk-match-enrichment", this::syncFootballDataUkEnrichment);
        log.info("Match enrichment completed: provider={}", Provider.FOOTBALL_DATA_UK);
    }

    private void syncFootballDataUkEnrichment() {
        boolean initialEnrichmentComplete = checkpointStore.isCompleted(
                Provider.FOOTBALL_DATA_UK, ENRICHMENT_INITIAL_CHECKPOINT);
        if (!initialEnrichmentComplete) {
            if (footballDataUkSyncService.syncInitialEnrichment()) {
                checkpointStore.markCompleted(Provider.FOOTBALL_DATA_UK, ENRICHMENT_INITIAL_CHECKPOINT);
            }
            return;
        }
        footballDataUkSyncService.syncActiveSeasonsEnrichment();
    }

    private void runStage(String name, Runnable operation) {
        long started = System.nanoTime();
        log.info("Match enrichment stage STARTED: {}", name);
        try {
            operation.run();
            log.info("Match enrichment stage COMPLETED: {} durationMs={}",
                    name, (System.nanoTime() - started) / 1_000_000);
        } catch (RuntimeException exception) {
            log.error("Match enrichment stage FAILED: {} durationMs={} errorType={} message={}",
                    name, (System.nanoTime() - started) / 1_000_000,
                    exception.getClass().getSimpleName(), exception.getMessage(), exception);
            throw exception;
        }
    }
}
