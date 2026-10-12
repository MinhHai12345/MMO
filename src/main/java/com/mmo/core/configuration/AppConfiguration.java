package com.mmo.core.configuration;

import com.mmo.core.utils.BeanUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfiguration {

    @Bean("beanUtils")
    public BeanUtils beanUtils() {
        return new BeanUtils();
    }
}
