package com.mmo.core.importer.reader;

import com.mmo.core.importer.model.ImportContext;
import org.springframework.batch.item.ItemReader;

public interface ItemReaderFactory<T> {
    boolean supports(String fileFormat);

    ItemReader<T> createReader(ImportContext context);
}
