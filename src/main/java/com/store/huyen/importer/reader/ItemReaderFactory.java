package com.store.huyen.importer.reader;

import com.store.huyen.importer.model.ImportContext;
import org.springframework.batch.item.ItemReader;

public interface ItemReaderFactory<T> {
    boolean supports(String fileFormat);

    ItemReader<T> createReader(ImportContext context);
}
