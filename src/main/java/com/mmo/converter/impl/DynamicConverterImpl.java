package com.mmo.converter.impl;

import com.mmo.converter.DynamicConverter;
import com.mmo.converter.Mapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DynamicConverterImpl implements DynamicConverter {
    private final List<Mapper<?, ?>> mappers;

    @Override
    @SuppressWarnings("unchecked")
    public <S, T> T convert(S source, Class<T> targetClass) {
        if (source == null) {
            return null;
        }

        return (T) mappers.stream()
                .filter(m -> m.supports(source.getClass(), targetClass))
                .findFirst()
                .map(map -> {
                    try {
                        return ((Mapper<S, T>) map).map(source, targetClass.getDeclaredConstructor().newInstance());
                    } catch (Exception e) {
                        throw new RuntimeException("Entity " + targetClass.getSimpleName() + " thiếu No-Args Constructor");
                    }
                })
                .orElseThrow(() -> new RuntimeException(
                        "Không tìm thấy Mapper từ " + source.getClass().getSimpleName() + " sang " + targetClass.getSimpleName()));
    }

    @Override
    public <S, T> List<T> convertAll(List<S> sources, Class<T> targetClass) {
        if (CollectionUtils.isEmpty(sources)) {
            return Collections.emptyList();
        }
        return sources.stream()
                .map(s -> convert(s, targetClass))
                .collect(Collectors.toList());
    }
}
