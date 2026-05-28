package com.mmo.module.fb.job;

import com.mmo.cronjob.entity.CronJob;
import com.mmo.cronjob.job.AbstractJob;
import com.mmo.module.fb.crawler.model.enums.Provider;
import com.mmo.module.fb.crawler.strategy.CrawlerStrategy;
import com.mmo.module.fb.crawler.strategy.CrawlerStrategyRegistry;
import jakarta.annotation.Resource;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

@Component
public class InitialFBDataJob extends AbstractJob<CronJob> {
    @Resource
    private CrawlerStrategyRegistry crawlerStrategyRegistry;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        System.out.println("-----Initial all Data for FB Job-----");
        CrawlerStrategy strategy = crawlerStrategyRegistry.getStrategy(Provider.SOFA_SCORE);
//        strategy.storeLeagues();
//        strategy.storeSeasons();
//        strategy.storeTeams();
//        strategy.storeMatches();
        strategy.storeMatchStatistics();

    }

}
