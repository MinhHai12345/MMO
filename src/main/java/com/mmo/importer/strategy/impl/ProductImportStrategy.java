package com.mmo.importer.strategy.impl;

import com.mmo.importer.model.product.ProductCsvModel;
import com.mmo.importer.strategy.ImportStrategy;
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
