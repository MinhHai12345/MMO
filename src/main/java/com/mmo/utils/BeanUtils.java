package com.mmo.utils;

import lombok.Getter;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;

public class BeanUtils implements ApplicationContextAware {

    @Getter
    private static ApplicationContext context;

    /**
     * Get bean of type {@code beanClazz}.
     *
     * @param beanClazz Class<T>
     * @param <T>       T
     * @return T
     */
    public static <T> T getBean(final Class<T> beanClazz) {
        return context.getBean(beanClazz);
    }

    /**
     * Get bean with name {@code name} of type {@code beanClazz}.
     *
     * @param name      String
     * @param beanClazz Class<T>
     * @param <T>       T
     * @return T
     */
    public static <T> T getBean(final String name, final Class<T> beanClazz) {
        return context.getBean(name, beanClazz);
    }

    /**
     * Get bean with name {@code name} of type {@code beanClazz}.
     *
     * @param name String
     * @return bean Object
     */
    public static Object getBean(final String name) {
        return context.getBean(name);
    }

    /**
     * Unproxy the {@code bean}
     *
     * @param proxy Object
     * @param <T>   T
     * @return T
     */
    @SuppressWarnings("unchecked")
    public static <T> T unproxy(final Object proxy) {
        final Object bean = AopProxyUtils.getSingletonTarget(proxy);
        return (T) (bean != null ? bean : proxy);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setApplicationContext(@NonNull final ApplicationContext applicationContext) throws BeansException {
        context = applicationContext; //NOSONAR
    }

}
