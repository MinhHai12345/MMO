package com.mmo.config;

import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

/** Makes Spring services available to Quartz-created job instances. */
@Configuration
public class QuartzJobAutowiringConfiguration {

    @Bean
    public SchedulerFactoryBeanCustomizer quartzJobFactoryCustomizer(
            AutowireCapableBeanFactory beanFactory) {
        SpringBeanJobFactory jobFactory = new SpringBeanJobFactory() {
            @Override
            protected Object createJobInstance(TriggerFiredBundle bundle) throws Exception {
                Object job = super.createJobInstance(bundle);
                beanFactory.autowireBean(job);
                return job;
            }
        };
        return schedulerFactory -> schedulerFactory.setJobFactory(jobFactory);
    }
}
