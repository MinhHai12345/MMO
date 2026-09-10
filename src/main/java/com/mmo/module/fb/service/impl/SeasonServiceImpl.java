package com.mmo.module.fb.service.impl;

import com.mmo.module.fb.provider.model.enums.Provider;
import com.mmo.module.fb.provider.strategy.CrawlerStrategyRegistry;
import com.mmo.module.fb.service.SeasonService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SeasonServiceImpl implements SeasonService {
    private final CrawlerStrategyRegistry crawlerStrategyRegistry;

    @Override
    public void storeAllSeasons() {
        crawlerStrategyRegistry.getStrategy(Provider.SOFA_SCORE).storeSeasons();
    }
}
