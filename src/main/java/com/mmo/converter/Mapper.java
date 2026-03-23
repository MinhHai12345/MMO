package com.mmo.converter;

import java.util.List;

public interface Mapper<S, T> {
    T map(S source, T target);

    boolean supports(Class<?> sourceClass, Class<?> targetClass);
}
