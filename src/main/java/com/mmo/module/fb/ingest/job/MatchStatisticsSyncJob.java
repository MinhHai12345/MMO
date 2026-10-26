package com.mmo.module.fb.ingest.job;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.job.AbstractJob;
import com.mmo.module.fb.ingest.provider.sofascore.SofaScoreCrawlStrategy;
import com.mmo.module.fb.ingest.service.SofaScoreBootstrapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class MatchStatisticsSyncJob extends AbstractJob<CronJob> {

    private final SofaScoreBootstrapService sofaScoreBootstrapService;
    private final SofaScoreCrawlStrategy sofaScoreCrawlStrategy;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        log.info("Starting SofaScore bootstrap and finished-match statistics/xG synchronization");
        sofaScoreBootstrapService.syncInitialDataIfRequired();
        sofaScoreCrawlStrategy.storeMatchStatistics();
        log.info("SofaScore bootstrap and statistics/xG synchronization completed");
    }
}
