package com.mmo.importer.reader;

import com.mmo.importer.model.ImportContext;
import com.mmo.importer.strategy.ImportStrategy;
import com.mmo.importer.strategy.ImportStrategyRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class CsvItemReaderFactory implements ItemReaderFactory<Object> {
    private final ImportStrategyRegistry strategyRegistry;

    @Override
    public boolean supports(final String fileFormat) {
        return "csv".equalsIgnoreCase(fileFormat);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ItemReader<Object> createReader(ImportContext context) {
        ImportStrategy<Object> strategy = (ImportStrategy<Object>) strategyRegistry.getStrategy(context.getImportType());
        return new FlatFileItemReaderBuilder<>()
                .name(strategy.getType() + "Reader")
                .resource(new FileSystemResource(context.getFilePath()))
                .encoding(StandardCharsets.UTF_8.name())
                .linesToSkip(1)
                .delimited()
                .names(strategy.getHeaders())
                .fieldSetMapper(new BeanWrapperFieldSetMapper<>() {{
                    setTargetType(strategy.getModelClass());
                }})
                .build();
    }
}
