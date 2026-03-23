package com.mmo.converter;

import java.lang.reflect.ParameterizedType;

public abstract class AbstractMapper<S, T> implements Mapper<S, T> {
    private final Class<S> sClass;
    private final Class<T> tClass;

    @SuppressWarnings("unchecked")
    protected AbstractMapper() {
        this.sClass = (Class<S>) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0];
        this.tClass = (Class<T>) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[1];
    }

    @Override
    public boolean supports(Class<?> sourceClass, Class<?> targetClass) {
        return sClass.isAssignableFrom(sourceClass) && tClass.equals(targetClass);
    }
}
