package com.mmo.module.fb.ingest.job;

import com.mmo.AppProperties;
import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.job.AbstractJob;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.sofascore.SofaScoreCrawlStrategy;
import com.mmo.module.fb.ingest.service.SofaScoreBootstrapService;
import com.mmo.module.fb.ingest.service.impl.SofaScorePlayerStatisticsIngestionService;
import com.mmo.module.fb.ingest.service.impl.UnderStatSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

/** Enriches canonical MMO fixtures with historical team and player statistics. */
@Slf4j
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class MatchEnrichmentJob extends AbstractJob<CronJob> {

    private final AppProperties appProperties;
    private final UnderStatSyncService underStatSyncService;
    private final SofaScoreBootstrapService sofaScoreBootstrapService;
    private final SofaScoreCrawlStrategy sofaScoreCrawlStrategy;
    private final SofaScorePlayerStatisticsIngestionService playerStatisticsIngestionService;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        Provider selected = appProperties.getIngestion().getProviders().getHistoricalStats();
        log.info("Match enrichment started: historicalStatsProvider={}", selected);

        if (selected == Provider.UNDER_STATS) {
            runStage("understat", underStatSyncService::syncActiveSeasons);
        } else if (selected == Provider.SOFA_SCORE) {
            runStage("sofascore-bootstrap", sofaScoreBootstrapService::syncInitialDataIfRequired);
            checkStop();
            runStage("sofascore-match-statistics", sofaScoreCrawlStrategy::storeMatchStatistics);
        } else {
            log.info("No historical match-statistics enrichment configured: provider={}", selected);
        }

        checkStop();
        runStage("player-statistics", playerStatisticsIngestionService::syncFinishedMatchPlayerStatistics);
        log.info("Match enrichment completed: historicalStatsProvider={}", selected);
    }

    private void runStage(String name, Runnable action) {
        long started = System.nanoTime();
        log.info("Match enrichment stage STARTED: {}", name);
        action.run();
        log.info("Match enrichment stage COMPLETED: {} durationMs={}",
                name, (System.nanoTime() - started) / 1_000_000);
    }

    private void checkStop() {
        if (shouldStop()) throw new IllegalStateException("Match enrichment interrupted");
    }
}
