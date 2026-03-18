package com.store.huyen.importer.reader;

import com.store.huyen.importer.model.ImportContext;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DynamicItemReaderFactory {
    private final List<ItemReaderFactory<Object>> factories;

    public ItemReader<Object> createReader(final ImportContext context) {
        return factories.stream()
                .filter(factory -> factory.supports(context.getFileFormat()))
                .findFirst()
                .map(factory -> factory.createReader(context))
                .orElseThrow(() -> new IllegalArgumentException(
                        "No reader factory found for file format: " + context.getFileFormat()
                ));
    }
}
