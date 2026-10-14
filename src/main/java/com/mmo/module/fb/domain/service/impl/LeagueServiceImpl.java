package com.mmo.module.fb.domain.service.impl;

import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.domain.service.LeagueService;
import com.mmo.module.fb.provider.strategy.CrawlerStrategyRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LeagueServiceImpl implements LeagueService {
    private final CrawlerStrategyRegistry crawlerStrategyRegistry;

    @Override
    public void storeAllLeagues() {
        crawlerStrategyRegistry.getStrategy(Provider.SOFA_SCORE).syncLeagues();
    }
}
