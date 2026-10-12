package com.mmo.core.converter;

public interface Mapper<S, T> {
    T map(S source, T target);

    boolean supports(Class<?> sourceClass, Class<?> targetClass);
}
