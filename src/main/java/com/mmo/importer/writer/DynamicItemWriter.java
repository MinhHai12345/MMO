package com.mmo.importer.writer;

import com.mmo.importer.strategy.ImportStrategy;
import com.mmo.importer.strategy.ImportStrategyRegistry;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DynamicItemWriter<T> implements ItemWriter<T> {
    private final ImportStrategyRegistry registry;

    @Setter
    private String importType;

    @Override
    @SuppressWarnings("unchecked")
    public void write(@NonNull Chunk<? extends T> chunk) throws Exception {
        final ImportStrategy<T> strategy = (ImportStrategy<T>) registry.getStrategy(importType);
        strategy.writeItems((List<T>) chunk.getItems());
    }
}
