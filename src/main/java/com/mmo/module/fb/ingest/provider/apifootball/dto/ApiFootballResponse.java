package com.mmo.module.fb.ingest.provider.apifootball.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiFootballResponse<T>(
        List<T> response,
        Object errors,
        ApiFootballPagingDto paging,
        Integer results
) {
    public boolean hasErrors() {
        if (errors == null) return false;
        if (errors instanceof Map<?, ?> map) return !map.isEmpty();
        if (errors instanceof List<?> list) return !list.isEmpty();
        if (errors instanceof String value) return !value.isBlank();
        return false;
    }
}
