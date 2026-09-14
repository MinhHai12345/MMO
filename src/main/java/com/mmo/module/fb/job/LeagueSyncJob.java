package com.mmo.module.fb.job;

import com.mmo.cronjob.entity.CronJob;
import com.mmo.cronjob.job.AbstractJob;
import com.mmo.module.fb.provider.model.enums.Provider;
import com.mmo.module.fb.provider.strategy.CrawlerStrategy;
import com.mmo.module.fb.provider.strategy.CrawlerStrategyRegistry;
import jakarta.annotation.Resource;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

@Component
public class LeagueSyncJob extends AbstractJob<CronJob> {
    @Resource
    private CrawlerStrategyRegistry crawlerStrategyRegistry;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        CrawlerStrategy strategy = crawlerStrategyRegistry.getStrategy(Provider.SOFA_SCORE);
        strategy.syncLeagues();
    }

}
