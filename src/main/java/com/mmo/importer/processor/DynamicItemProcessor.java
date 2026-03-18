package com.mmo.importer.processor;

import com.mmo.importer.strategy.ImportStrategy;
import com.mmo.importer.strategy.ImportStrategyRegistry;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DynamicItemProcessor<T> implements ItemProcessor<T, T> {
    private final ImportStrategyRegistry registry;

    @Setter
    private String importType;

    @Override
    @SuppressWarnings("unchecked")
    public T process(@NonNull T item) throws Exception {
        final ImportStrategy<T> strategy = (ImportStrategy<T>) registry.getStrategy(importType);
        strategy.process(item);
        return item;
    }
}
