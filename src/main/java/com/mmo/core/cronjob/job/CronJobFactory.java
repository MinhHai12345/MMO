package com.mmo.core.cronjob.job;

import org.quartz.SchedulerContext;
import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.BeanWrapper;
import org.springframework.context.ApplicationContext;
import org.springframework.lang.NonNull;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

/**
 * Creates Quartz jobs through Spring so constructor injection and normal
 * Spring dependency injection are supported.
 */
public class CronJobFactory extends SpringBeanJobFactory {

    private AutowireCapableBeanFactory beanFactory;
    private SchedulerContext schedulerContext = new SchedulerContext();

    @Override
    public void setApplicationContext(final ApplicationContext context) {
        beanFactory = context.getAutowireCapableBeanFactory();
    }

    @Override
    public void setSchedulerContext(final SchedulerContext schedulerContext) {
        super.setSchedulerContext(schedulerContext);
        this.schedulerContext = schedulerContext;
    }

    @Override
    @NonNull
    protected Object createJobInstance(@NonNull final TriggerFiredBundle bundle) {
        // Do not call super.createJobInstance(): AdaptableJobFactory creates
        // jobs through a no-argument constructor, which breaks jobs using
        // Lombok @RequiredArgsConstructor / constructor injection.
        final Object job = beanFactory.createBean(bundle.getJobDetail().getJobClass());

        // Preserve SpringBeanJobFactory's property population from scheduler,
        // job and trigger data maps.
        final BeanWrapper beanWrapper = PropertyAccessorFactory.forBeanPropertyAccess(job);
        final MutablePropertyValues propertyValues = new MutablePropertyValues();
        propertyValues.addPropertyValues(schedulerContext);
        propertyValues.addPropertyValues(bundle.getJobDetail().getJobDataMap());
        propertyValues.addPropertyValues(bundle.getTrigger().getJobDataMap());
        beanWrapper.setPropertyValues(propertyValues, true);

        return job;
    }
}
