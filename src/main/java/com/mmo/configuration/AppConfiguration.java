package com.mmo.configuration;

import com.mmo.utils.BeanUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfiguration {

    @Bean("beanUtils")
    public BeanUtils beanUtils() {
        return new BeanUtils();
    }
}
