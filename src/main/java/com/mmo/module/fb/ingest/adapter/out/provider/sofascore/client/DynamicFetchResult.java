package com.mmo.module.fb.ingest.adapter.out.provider.sofascore.client;

import java.util.HashMap;
import java.util.Map;

/**
 * Internal response accumulator used by the SofaScore dynamic endpoint client.
 */
public class DynamicFetchResult {
    private final Map<String, Object> parsedData = new HashMap<>();

    public void put(String endpoint, Object parsedObject) {
        parsedData.put(endpoint, parsedObject);
    }

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
