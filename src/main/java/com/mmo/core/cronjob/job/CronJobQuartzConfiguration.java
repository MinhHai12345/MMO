package com.mmo.core.cronjob.job;

import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the shared CronJobFactory with the application's Quartz scheduler.
 */
@Configuration
public class CronJobQuartzConfiguration {

    @Bean
    public SchedulerFactoryBeanCustomizer cronJobFactoryCustomizer(ApplicationContext applicationContext) {
        CronJobFactory jobFactory = new CronJobFactory();
        jobFactory.setApplicationContext(applicationContext);
        return schedulerFactory -> schedulerFactory.setJobFactory(jobFactory);
    }
}
