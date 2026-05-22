package com.mmo.configuration;

import com.mmo.cronjob.job.CronJobFactory;
import com.mmo.cronjob.listener.JobStateListener;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CronJobConfiguration {

    @Bean
    public SchedulerFactoryBeanCustomizer schedulerFactoryBeanCustomizer(final ApplicationContext applicationContext) {
        return schedulerFactoryBean -> {
            final CronJobFactory jobFactory = new CronJobFactory();
            jobFactory.setApplicationContext(applicationContext);
            schedulerFactoryBean.setJobFactory(jobFactory);

            schedulerFactoryBean.setGlobalJobListeners(new JobStateListener());
        };
    }

}
