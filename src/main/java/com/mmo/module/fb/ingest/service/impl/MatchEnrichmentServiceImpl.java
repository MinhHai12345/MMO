package com.mmo.module.fb.ingest.service.impl;

import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.entity.ProviderSyncCheckpoint;
import com.mmo.module.fb.ingest.repository.ProviderSyncCheckpointRepository;
import com.mmo.module.fb.ingest.service.MatchEnrichmentService;
import com.mmo.module.fb.ingest.service.SofaScoreBootstrapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Provider selection and sequencing belong to the application layer, not Quartz jobs. */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchEnrichmentServiceImpl implements MatchEnrichmentService {

    private static final String CORE_INITIAL_CHECKPOINT = "CORE_INITIAL_BACKFILL";
    private static final String ENRICHMENT_INITIAL_CHECKPOINT = "MATCH_ENRICHMENT_INITIAL";

    private final AppProperties properties;
    private final ProviderSyncCheckpointRepository checkpointRepository;
    private final FootballDataUkSyncService footballDataUkSyncService;
    private final UnderStatSyncService underStatSyncService;
    private final SofaScoreBootstrapService sofaScoreBootstrapService;
    private final SofaScoreMatchStatisticsIngestionService sofaScoreMatchStatisticsIngestionService;
    private final SofaScorePlayerStatisticsIngestionService playerStatisticsIngestionService;

    @Override
    public void enrichFinishedMatches() {
        Provider selected = properties.getIngestion().getProviders().getHistoricalStats();
        log.info("Match enrichment started: historicalStatsProvider={}", selected);

        runStage("football-data-uk-enrichment", this::syncFootballDataUkEnrichment);

        if (selected == Provider.UNDER_STATS) {
            runStage("understat-historical-statistics", underStatSyncService::syncActiveSeasons);
        } else if (selected == Provider.SOFA_SCORE) {
            runStage("sofascore-bootstrap", sofaScoreBootstrapService::syncInitialDataIfRequired);
            runStage("sofascore-match-statistics", sofaScoreMatchStatisticsIngestionService::storeMatchStatistics);
        } else {
            log.info("No historical match-statistics provider configured: {}", selected);
        }

        runStage("player-statistics", playerStatisticsIngestionService::syncFinishedMatchPlayerStatistics);
        log.info("Match enrichment completed: historicalStatsProvider={}", selected);
    }

    private void syncFootballDataUkEnrichment() {
        boolean coreReady = checkpointRepository.existsByProviderAndCheckpointKey(
                Provider.FOOTBALL_DATA_UK, CORE_INITIAL_CHECKPOINT);
        if (!coreReady) {
            log.info("Skipping Football-Data UK match enrichment until the 2014+ Core CSV import is complete");
            return;
        }

        boolean initialEnrichmentComplete = checkpointRepository.existsByProviderAndCheckpointKey(
                Provider.FOOTBALL_DATA_UK, ENRICHMENT_INITIAL_CHECKPOINT);
        if (!initialEnrichmentComplete) {
            if (footballDataUkSyncService.syncInitialEnrichment()) {
                ProviderSyncCheckpoint checkpoint = checkpointRepository
                        .findByProviderAndCheckpointKey(Provider.FOOTBALL_DATA_UK, ENRICHMENT_INITIAL_CHECKPOINT)
                        .orElseGet(ProviderSyncCheckpoint::new);
                checkpoint.setProvider(Provider.FOOTBALL_DATA_UK);
                checkpoint.setCheckpointKey(ENRICHMENT_INITIAL_CHECKPOINT);
                checkpoint.setCompletedAt(java.time.Instant.now());
                checkpointRepository.save(checkpoint);
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
