package com.mmo.module.fb.ingest.job;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.job.AbstractJob;
import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.ingest.provider.strategy.CrawlerStrategy;
import com.mmo.module.fb.ingest.service.StandingsIngestionService;
import com.mmo.module.fb.provider.strategy.CrawlerStrategyRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class MatchSeasonFixtureSyncJob extends AbstractJob<CronJob> {
    private final CrawlerStrategyRegistry crawlerStrategyRegistry;
    private final StandingsIngestionService standingsIngestionService;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        CrawlerStrategy strategy = crawlerStrategyRegistry.getStrategy(Provider.API_FOOTBALL);
        strategy.storeSeasons();
        strategy.syncTeams();
        strategy.syncAllCurrentSeasonFixtures();
        standingsIngestionService.syncStandings();
    }
}
