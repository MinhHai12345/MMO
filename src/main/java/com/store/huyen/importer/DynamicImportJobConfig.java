package com.store.huyen.importer;

import com.store.huyen.importer.model.ImportContext;
import com.store.huyen.importer.processor.DynamicItemProcessor;
import com.store.huyen.importer.reader.CsvItemReaderFactory;
import com.store.huyen.importer.reader.DynamicItemReaderFactory;
import com.store.huyen.importer.writer.DynamicItemWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableBatchProcessing
@RequiredArgsConstructor
public class DynamicImportJobConfig {
    private final JobRepository jobRepository;
    private final DynamicItemReaderFactory readerFactory;
    private final DynamicItemProcessor<Object> processor;
    private final DynamicItemWriter<Object> writer;
    private final PlatformTransactionManager transactionManager;

    public Step step(ImportContext context) {
        processor.setImportType(context.getImportType());
        writer.setImportType(context.getImportType());
        return new StepBuilder("importStep-" + context.getImportType(), jobRepository)
                .<Object, Object>chunk(50, transactionManager)
                .reader(readerFactory.createReader(context))
                .processor(processor)
                .writer(writer)
                .build();
    }

    public Job buildJob(ImportContext context) {
        Step step = step(context);
        return new JobBuilder("importJob-" + context.getImportType(), jobRepository)
                .start(step)
                .build();
    }


}

