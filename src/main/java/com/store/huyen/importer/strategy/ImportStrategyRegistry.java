package com.store.huyen.importer.strategy;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ImportStrategyRegistry {

    private final List<ImportStrategy<?>> strategies;

    public ImportStrategy<?> getStrategy(final String type) {
        return strategies.stream()
                .filter(s -> s.getType().equalsIgnoreCase(type))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No import strategy for type: " + type));
    }
}
