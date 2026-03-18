package com.store.huyen.importer.writer;

import com.store.huyen.importer.strategy.ImportStrategy;
import com.store.huyen.importer.strategy.ImportStrategyRegistry;
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
