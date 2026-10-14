package com.mmo.module.fb.domain.service.impl;

import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.domain.service.SeasonService;
import com.mmo.module.fb.provider.strategy.CrawlerStrategyRegistry;
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
