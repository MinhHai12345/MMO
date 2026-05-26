package com.mmo.module.fb.service.impl;

import com.mmo.module.fb.crawler.model.enums.Provider;
import com.mmo.module.fb.crawler.strategy.CrawlerStrategyRegistry;
import com.mmo.module.fb.service.LeagueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LeagueServiceImpl implements LeagueService {
    private final CrawlerStrategyRegistry crawlerStrategyRegistry;

    @Override
    public void storeAllLeagues() {
        crawlerStrategyRegistry.getStrategy(Provider.SOFA_SCORE).storeLeagues();
    }
}
