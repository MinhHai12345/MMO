package com.mmo.module.fb.service.impl;

import com.mmo.module.fb.provider.model.enums.Provider;
import com.mmo.module.fb.provider.strategy.CrawlerStrategyRegistry;
import com.mmo.module.fb.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TeamServiceImpl implements TeamService {
    private final CrawlerStrategyRegistry crawlerStrategyRegistry;

    @Override
    public void storeAllTeams() {
        crawlerStrategyRegistry.getStrategy(Provider.SOFA_SCORE).storeTeams();
    }
}
