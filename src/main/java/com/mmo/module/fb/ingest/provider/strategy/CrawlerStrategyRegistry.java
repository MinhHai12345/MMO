package com.mmo.module.fb.provider.strategy;

import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.ingest.provider.strategy.CrawlerStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CrawlerStrategyRegistry {
    private final List<CrawlerStrategy> strategies;

    public CrawlerStrategy getStrategy(final Provider provider) {
        return strategies.stream()
                .filter(strategy -> strategy.getProvider() == provider)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Don't support for source: " + provider.name()));
    }
}
