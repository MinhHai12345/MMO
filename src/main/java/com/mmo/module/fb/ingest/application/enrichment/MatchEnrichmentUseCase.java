package com.mmo.module.fb.ingest.application.enrichment;

import com.mmo.module.fb.ingest.application.lock.IngestionDistributedLock;
import com.mmo.module.fb.ingest.application.port.out.FootballDataUkIngestionPort;
import com.mmo.module.fb.ingest.application.port.out.PlayerStatisticsIngestionPort;
import com.mmo.module.fb.ingest.application.port.out.ProviderCheckpointStore;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.service.MatchEnrichmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Coordinates match enrichment from the canonical Football-Data UK CSV source.
 * Match fixtures and match-level statistics are sourced from Football-Data UK;
 * player statistics remain a separate enrichment concern.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchEnrichmentUseCase implements MatchEnrichmentService {

    private static final String CORE_INITIAL_CHECKPOINT = "CORE_INITIAL_BACKFILL";
    private static final String ENRICHMENT_INITIAL_CHECKPOINT = "MATCH_ENRICHMENT_INITIAL";

    private final IngestionDistributedLock distributedLock;
    private final ProviderCheckpointStore checkpointStore;
    private final FootballDataUkIngestionPort footballDataUkSyncService;
    private final PlayerStatisticsIngestionPort playerStatisticsIngestionService;

    @Override
    public void enrichFinishedMatches() {
        boolean executed = distributedLock.execute(IngestionDistributedLock.GLOBAL_INGEST_LOCK,
                this::enrichFinishedMatchesLocked);
        if (!executed) {
            log.warn("Match enrichment skipped because another ingestion workflow is running");
        }
    }

    private void enrichFinishedMatchesLocked() {
        log.info("Match enrichment started: matchDataProvider={}", Provider.FOOTBALL_DATA_UK);

        boolean coreReady = checkpointStore.isCompleted(
                Provider.FOOTBALL_DATA_UK, CORE_INITIAL_CHECKPOINT);
        if (!coreReady) {
            log.info("Skipping match enrichment until the 2014+ Football-Data UK Core CSV import is complete");
            return;
        }

        runStage("football-data-uk-match-enrichment", this::syncFootballDataUkEnrichment);
        runStage("player-statistics", playerStatisticsIngestionService::syncFinishedMatchPlayerStatistics);

        log.info("Match enrichment completed: matchDataProvider={}", Provider.FOOTBALL_DATA_UK);
    }

    /**
     * Backfill all imported historical seasons once, then refresh only the current season.
     * The checkpoint is marked only after the full historical enrichment succeeds.
     */
    private void syncFootballDataUkEnrichment() {
        boolean initialEnrichmentComplete = checkpointStore.isCompleted(
                Provider.FOOTBALL_DATA_UK, ENRICHMENT_INITIAL_CHECKPOINT);
        if (!initialEnrichmentComplete) {
            if (footballDataUkSyncService.syncInitialEnrichment()) {
                checkpointStore.markCompleted(Provider.FOOTBALL_DATA_UK, ENRICHMENT_INITIAL_CHECKPOINT);
            }
            return;
        }

        footballDataUkSyncService.syncCurrentSeasonEnrichment();
    }

    private void runStage(String name, Runnable operation) {
        long started = System.nanoTime();
        log.info("Match enrichment stage STARTED: {}", name);
        operation.run();
        log.info("Match enrichment stage COMPLETED: {} durationMs={}",
                name, (System.nanoTime() - started) / 1_000_000);
    }
}
