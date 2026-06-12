package com.mmo.module.fb.crawler.model;

import java.util.HashMap;
import java.util.Map;

public class DynamicFetchResult {
    private final Map<String, Object> parsedData = new HashMap<>();

    public void put(String endpoint, Object parsedObject) {
        parsedData.put(endpoint, parsedObject);
    }

    /**
     * Lấy dữ liệu đã parse một cách Type-safe
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String endpoint, Class<T> clazz) {
        Object obj = parsedData.get(endpoint);
        if (obj != null && clazz.isAssignableFrom(obj.getClass())) {
            return (T) obj;
        }
        return null;
    }

    public boolean hasData(String endpoint) {
        return parsedData.containsKey(endpoint) && parsedData.get(endpoint) != null;
    }
}
