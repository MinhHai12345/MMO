package com.store.huyen.importer.strategy.impl;

import com.store.huyen.importer.model.product.ProductCsvModel;
import com.store.huyen.importer.strategy.ImportStrategy;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProductImportStrategy implements ImportStrategy<ProductCsvModel> {

    @Override
    public String getType() {
        return "Product";
    }

    @Override
    public Class<ProductCsvModel> getModelClass() {
        return ProductCsvModel.class;
    }

    @Override
    public String[] getHeaders() {
        return new String[] { "product_id", "product_name", "product_price", "product_description" };
    }

    @Override
    public void process(final ProductCsvModel item) {

    }

    @Override
    public void writeItems(final List<ProductCsvModel> items) {

    }
}
