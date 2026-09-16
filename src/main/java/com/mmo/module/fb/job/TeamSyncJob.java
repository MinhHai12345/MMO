package com.mmo.module.fb.job;

import com.mmo.cronjob.entity.CronJob;
import com.mmo.cronjob.job.AbstractJob;
import com.mmo.module.fb.provider.model.enums.Provider;
import com.mmo.module.fb.provider.strategy.CrawlerStrategy;
import com.mmo.module.fb.provider.strategy.CrawlerStrategyRegistry;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class TeamSyncJob extends AbstractJob<CronJob> {
    @Resource
    private CrawlerStrategyRegistry crawlerStrategyRegistry;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        CrawlerStrategy strategy = crawlerStrategyRegistry.getStrategy(Provider.SOFA_SCORE);
        strategy.syncTeams();
    }

}
