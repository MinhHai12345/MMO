package com.mmo.module.fb.crawler.strategy;

import com.mmo.module.fb.channel.model.Platform;
import com.mmo.module.fb.channel.strategy.ContentStrategy;
import com.mmo.module.fb.crawler.model.Provider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CrawlerStrategyRegistry {
    private final List<CrawlerStrategy> strategies;

    public CrawlerStrategy getStrategy(final Provider provider) {
        return strategies.stream()
                .filter(s -> s.getProvider().equals(provider))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Don't support for source: " + provider.name()));
    }
}
