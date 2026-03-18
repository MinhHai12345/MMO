package com.mmo.importer.strategy;

import java.util.List;

public interface ImportStrategy<T> {

    String getType();

    Class<T> getModelClass();

    String[] getHeaders();

    void process(T item);

    void writeItems(List<T> items);
}
