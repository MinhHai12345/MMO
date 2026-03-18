package com.store.huyen.importer.reader;

import com.store.huyen.importer.model.ImportContext;
import com.store.huyen.importer.strategy.ImportStrategy;
import com.store.huyen.importer.strategy.ImportStrategyRegistry;
import com.store.huyen.importer.util.ExcelUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.util.Iterator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ExcelItemReaderFactory implements ItemReaderFactory<Object> {
    private final ImportStrategyRegistry strategyRegistry;

    @Override
    public boolean supports(String fileFormat) {
        return "xls".equalsIgnoreCase(fileFormat) || "xlsx".equalsIgnoreCase(fileFormat);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ItemReader<Object> createReader(ImportContext context) {
        ImportStrategy<Object> strategy = (ImportStrategy<Object>) strategyRegistry.getStrategy(context.getImportType());
        try {
//            FileInputStream fis = new FileInputStream(context.getFilePath());
//            org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(fis);
//            org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(0);
//
//            List<Object> items = sheet.iterator().next() == null ? List.of() :
//                    ExcelUtils.mapRowsToModel(sheet, strategy.getModelClass());

//            Iterator<Object> iterator = items.iterator();
//
//            return iterator::next;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read Excel file: " + context.getFilePath(), e);
        }
        return null;
    }
}
