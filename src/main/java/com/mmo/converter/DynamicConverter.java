package com.mmo.converter;

import java.util.List;

public interface DynamicConverter {
    <S, T> T convert(S source, Class<T> targetClass);

    <S, T> List<T> convertAll(List<S> sources, Class<T> targetClass);
}
