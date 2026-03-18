package com.mmo.importer.reader;

import com.mmo.importer.model.ImportContext;
import org.springframework.batch.item.ItemReader;

public interface ItemReaderFactory<T> {
    boolean supports(String fileFormat);

    ItemReader<T> createReader(ImportContext context);
}
