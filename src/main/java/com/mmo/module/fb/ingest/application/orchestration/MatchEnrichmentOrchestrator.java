package com.mmo.module.fb.ingest.application.orchestration;

import com.mmo.module.fb.ingest.application.lock.IngestionDistributedLock;
import com.mmo.module.fb.ingest.application.port.in.MatchEnrichmentUseCase;
import com.mmo.module.fb.ingest.application.port.out.ProviderCheckpointPort;
import com.mmo.module.fb.ingest.application.port.out.provider.SeasonBatchIngestionPort;
import com.mmo.module.fb.ingest.application.registry.SeasonBatchIngestionRegistry;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.Dataset;
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
public class MatchEnrichmentOrchestrator implements MatchEnrichmentUseCase {

    private static final String ENRICHMENT_INITIAL_CHECKPOINT = "MATCH_ENRICHMENT_INITIAL";

    private final IngestionDistributedLock distributedLock;
    private final ProviderCheckpointPort checkpointStore;
    private final IngestionPlanFactory planFactory;
    private final SeasonBatchIngestionRegistry seasonBatchRegistry;

    @Override
    public void enrichFinishedMatches() {
        boolean executed = distributedLock.execute(IngestionDistributedLock.GLOBAL_INGEST_LOCK,
                this::enrichFinishedMatchesLocked);
        if (!executed) {
            log.warn("Match enrichment skipped because another ingestion workflow is running");
        }
    }

    private void enrichFinishedMatchesLocked() {
        IngestionPlan historicalPlan = planFactory.historicalPlan();
        java.util.List<SeasonBatchIngestionPort> enrichmentProviders = seasonBatchRegistry.all().stream()
                .filter(provider -> supportsAnyEnrichmentDataset(provider.getProvider()))
                .filter(provider -> !seasonIdsForProvider(historicalPlan, provider.getProvider()).isEmpty())
                .toList();
        if (enrichmentProviders.isEmpty()) {
            log.info("Match enrichment skipped: no registered season-batch provider supports enrichment datasets");
            return;
        }

        IngestionPlan activePlan = planFactory.activeSeasonPlan();
        java.util.List<String> failedStages = new java.util.ArrayList<>();
        for (SeasonBatchIngestionPort provider : enrichmentProviders) {
            Provider providerId = provider.getProvider();
            java.util.List<String> providerFailures = new java.util.ArrayList<>();
            java.util.List<Long> historicalSeasonIds = seasonIdsForProvider(historicalPlan, providerId);
            java.util.List<Long> activeSeasonIds = seasonIdsForProvider(activePlan, providerId);

            log.info("Match enrichment plan: provider={}, historicalSeasons={}, activeSeasons={}",
                    providerId, historicalSeasonIds.size(), activeSeasonIds.size());
            for (Long seasonId : historicalSeasonIds) {
                try {
                    runStage(providerId + "/historical-enrichment/season=" + seasonId,
                            () -> provider.syncEnrichmentSeason(seasonId, true));
                } catch (RuntimeException exception) {
                    providerFailures.add(providerId + "/historical/season=" + seasonId
                            + ": " + exception.getMessage());
                }
            }
            for (Long seasonId : activeSeasonIds) {
                try {
                    runStage(providerId + "/active-enrichment/season=" + seasonId,
                            () -> provider.syncEnrichmentSeason(seasonId, false));
                } catch (RuntimeException exception) {
                    providerFailures.add(providerId + "/active/season=" + seasonId
                            + ": " + exception.getMessage());
                }
            }
            failedStages.addAll(providerFailures);
            if (providerFailures.isEmpty()) {
                checkpointStore.markCompleted(providerId, ENRICHMENT_INITIAL_CHECKPOINT);
            }
        }
        if (!failedStages.isEmpty()) {
            throw new IllegalStateException("Match enrichment incomplete: " + failedStages);
        }
        log.info("Match enrichment completed for providers={}",
                enrichmentProviders.stream().map(SeasonBatchIngestionPort::getProvider).toList());
    }

    private boolean supportsAnyEnrichmentDataset(Provider provider) {
        return seasonBatchRegistry.supports(provider, Dataset.MATCH_STATISTICS)
                || seasonBatchRegistry.supports(provider, Dataset.MATCH_XG)
                || seasonBatchRegistry.supports(provider, Dataset.HISTORICAL_ODDS);
    }

    private java.util.List<Long> seasonIdsForProvider(IngestionPlan plan, Provider provider) {
        return plan.units().stream()
                .filter(unit -> unit.provider() == provider
                        && unit.role() == IngestionWorkUnit.Role.PRIMARY
                        && (unit.dataset() == Dataset.MATCH_STATISTICS
                        || unit.dataset() == Dataset.MATCH_XG
                        || unit.dataset() == Dataset.HISTORICAL_ODDS)
                        && seasonBatchRegistry.supports(provider, unit.dataset()))
                .map(IngestionWorkUnit::seasonId).distinct().sorted().toList();
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
