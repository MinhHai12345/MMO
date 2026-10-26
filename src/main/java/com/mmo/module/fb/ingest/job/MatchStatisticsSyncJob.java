package com.mmo.module.fb.ingest.job;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.job.AbstractJob;
import com.mmo.module.fb.ingest.provider.sofascore.SofaScoreCrawlStrategy;
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

    private final SofaScoreCrawlStrategy sofaScoreCrawlStrategy;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        log.info("Starting SofaScore core-data bootstrap and current-season statistics synchronization");
        sofaScoreCrawlStrategy.syncInitialDataIfRequired();
        sofaScoreCrawlStrategy.storeMatchStatistics();
        log.info("SofaScore core-data bootstrap and statistics synchronization completed");
    }
}
