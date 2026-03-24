package com.mmo.converter.impl;

import com.mmo.converter.DynamicConverter;
import com.mmo.converter.Mapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DynamicConverterImpl implements DynamicConverter {
    private final List<Mapper<?, ?>> mappers;

    @Override
    public <S, T> T convert(S source, Class<T> targetClass) {
        if (source == null) return null;
        try {
            T newInstance = targetClass.getDeclaredConstructor().newInstance();
            return convert(source, newInstance);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S, T> T convert(S source, T target) {
        if (source == null || target == null) {
            return target;
        }
        return (T) mappers.stream()
                .filter(m -> m.supports(source.getClass(), target.getClass()))
                .findFirst()
                .map(map -> ((Mapper<S, T>) map).map(source, target)) // Map trực tiếp vào target
                .orElseThrow(() -> new RuntimeException(
                        "Không tìm thấy Mapper từ " + source.getClass().getSimpleName() + " sang " + target.getClass().getSimpleName()));
    }

    @Override
    public <S, T> List<T> convertAll(Collection<S> sources, Class<T> targetClass) {
        if (CollectionUtils.isEmpty(sources)) {
            return Collections.emptyList();
        }
        return sources.stream()
                .map(s -> convert(s, targetClass))
                .collect(Collectors.toList());
    }
}
