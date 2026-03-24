package com.mmo.converter;

import java.util.Collection;
import java.util.List;

public interface DynamicConverter {
    <S, T> T convert(S source, Class<T> targetClass);

    <S, T> T convert(S source, T target);

    <S, T> List<T> convertAll(Collection<S> sources, Class<T> targetClass);
}
