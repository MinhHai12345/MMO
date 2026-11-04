package com.mmo.module.fb.ingest.service.impl;

import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.enums.Provider;
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

    private final AppProperties properties;
    private final UnderStatSyncService underStatSyncService;
    private final SofaScoreBootstrapService sofaScoreBootstrapService;
    private final SofaScoreMatchStatisticsIngestionService sofaScoreMatchStatisticsIngestionService;
    private final SofaScorePlayerStatisticsIngestionService playerStatisticsIngestionService;

    @Override
    public void enrichFinishedMatches() {
        Provider selected = properties.getIngestion().getProviders().getHistoricalStats();
        log.info("Match enrichment started: historicalStatsProvider={}", selected);

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

    private void runStage(String name, Runnable operation) {
        long started = System.nanoTime();
        log.info("Match enrichment stage STARTED: {}", name);
        operation.run();
        log.info("Match enrichment stage COMPLETED: {} durationMs={}",
                name, (System.nanoTime() - started) / 1_000_000);
    }
}
