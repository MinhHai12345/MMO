package com.mmo.module.fb.channel.strategy;

import com.mmo.module.fb.channel.model.Platform;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ContentStrategyRegistry {
    private final List<ContentStrategy> strategies;

    public ContentStrategy getStrategy(final Platform platform) {
        return strategies.stream()
                .filter(s -> s.getPlatform().equals(platform))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Don't support for platform: " + platform));
    }
}
