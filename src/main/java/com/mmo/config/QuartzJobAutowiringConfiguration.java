package com.mmo.config;

import com.mmo.core.cronjob.job.CronJobFactory;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the shared CronJobFactory for Quartz jobs managed by the application.
 */
@Configuration
public class QuartzJobAutowiringConfiguration {

    @Bean
    public SchedulerFactoryBeanCustomizer quartzJobFactoryCustomizer(ApplicationContext applicationContext) {
        CronJobFactory jobFactory = new CronJobFactory();
        jobFactory.setApplicationContext(applicationContext);
        return schedulerFactory -> schedulerFactory.setJobFactory(jobFactory);
    }
}
