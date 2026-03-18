package com.mmo.importer.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmo.importer.model.ImportContext;
import com.mmo.importer.strategy.ImportStrategy;
import com.mmo.importer.strategy.ImportStrategyRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Iterator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JsonItemReaderFactory implements ItemReaderFactory<Object> {
    private final ImportStrategyRegistry strategyRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean supports(final String fileFormat) {
        return "json".equalsIgnoreCase(fileFormat);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ItemReader<Object> createReader(ImportContext context) {
        ImportStrategy<Object> strategy = (ImportStrategy<Object>) strategyRegistry.getStrategy(context.getImportType());
        try {
            List<Object> list = objectMapper.readValue(new File(context.getFilePath()),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, strategy.getModelClass())
            );
            Iterator<Object> iterator = list.iterator();
            return iterator::next;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read JSON file: " + context.getFilePath(), e);
        }
    }
}
