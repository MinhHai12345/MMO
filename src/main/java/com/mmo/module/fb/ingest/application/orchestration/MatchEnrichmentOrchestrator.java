package com.mmo.module.fb.ingest.application.orchestration;

import com.mmo.module.fb.ingest.application.lock.IngestionDistributedLock;
import com.mmo.module.fb.ingest.provider.spi.SeasonBatchProvider;
import com.mmo.module.fb.ingest.application.port.out.ProviderCheckpointStore;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.Dataset;
import com.mmo.module.fb.ingest.application.port.in.MatchEnrichmentService;
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

    private static final String ENRICHMENT_INITIAL_CHECKPOINT = "MATCH_ENRICHMENT_INITIAL";

    private final IngestionDistributedLock distributedLock;
    private final ProviderCheckpointStore checkpointStore;
    private final IngestionPlanFactory planFactory;
    private final SeasonBatchProviderRegistry seasonBatchProviderRegistry;

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

        java.util.List<String> failedStages = new java.util.ArrayList<>();
        java.util.List<Long> historicalSeasonIds = plan.units().stream()
                .filter(unit -> unit.provider() == Provider.FOOTBALL_DATA_UK
                        && unit.role() == IngestionWorkUnit.Role.PRIMARY
                        && (unit.dataset() == Dataset.MATCH_STATISTICS
                        || unit.dataset() == Dataset.MATCH_XG
                        || unit.dataset() == Dataset.HISTORICAL_ODDS))
                .map(IngestionWorkUnit::seasonId).distinct().sorted().toList();
        for (Long seasonId : historicalSeasonIds) {
            try {
                runStage("football-data-uk/historical-enrichment/season=" + seasonId,
                        () -> seasonBatchProviderRegistry.require(Provider.FOOTBALL_DATA_UK).syncEnrichmentSeason(seasonId, true));
            } catch (RuntimeException exception) {
                failedStages.add("historical/season=" + seasonId + ": " + exception.getMessage());
            }
        }

        IngestionPlan activePlan = planFactory.activeSeasonPlan();
        java.util.List<Long> activeSeasonIds = activePlan.units().stream()
                .filter(unit -> unit.provider() == Provider.FOOTBALL_DATA_UK
                        && unit.role() == IngestionWorkUnit.Role.PRIMARY
                        && (unit.dataset() == Dataset.MATCH_STATISTICS
                        || unit.dataset() == Dataset.MATCH_XG
                        || unit.dataset() == Dataset.HISTORICAL_ODDS))
                .map(IngestionWorkUnit::seasonId).distinct().sorted().toList();
        for (Long seasonId : activeSeasonIds) {
            try {
                runStage("football-data-uk/active-enrichment/season=" + seasonId,
                        () -> seasonBatchProviderRegistry.require(Provider.FOOTBALL_DATA_UK).syncEnrichmentSeason(seasonId, false));
            } catch (RuntimeException exception) {
                failedStages.add("active/season=" + seasonId + ": " + exception.getMessage());
            }
        }
        if (failedStages.isEmpty()) {
            checkpointStore.markCompleted(Provider.FOOTBALL_DATA_UK, ENRICHMENT_INITIAL_CHECKPOINT);
        }
        if (!failedStages.isEmpty()) {
            throw new IllegalStateException("Match enrichment incomplete: " + failedStages);
        }
        log.info("Match enrichment completed: provider={}", Provider.FOOTBALL_DATA_UK);
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
