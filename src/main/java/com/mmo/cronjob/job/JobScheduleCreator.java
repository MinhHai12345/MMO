package com.mmo.cronjob.job;

import com.mmo.cronjob.exception.InvalidCronJobException;
import com.mmo.utils.DateTimeUtils;
import org.quartz.CronTrigger;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.quartz.CronTriggerFactoryBean;
import org.springframework.scheduling.quartz.JobDetailFactoryBean;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.TimeZone;

@Component
public class JobScheduleCreator {

    /**
     * Create a <code>JobDetail</code>.
     *
     * @param jobClass  Class<? extends QuartzJobBean>
     * @param isDurable boolean
     * @param context   ApplicationContext
     * @param jobName   String
     * @param jobGroup  String
     * @return JobDetail
     */
    public JobDetail createJob(final Class<? extends QuartzJobBean> jobClass, final boolean isDurable,
                               final ApplicationContext context, final String jobName, final String jobGroup) {
        final JobDetailFactoryBean factoryBean = new JobDetailFactoryBean();
        factoryBean.setJobClass(jobClass);
        factoryBean.setDurability(isDurable);
        factoryBean.setApplicationContext(context);
        factoryBean.setName(jobName);
        factoryBean.setGroup(jobGroup);

        // set job data map
        final JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put(jobName + jobGroup, jobClass.getName());
        factoryBean.setJobDataMap(jobDataMap);
        factoryBean.afterPropertiesSet();
        return factoryBean.getObject();
    }

    /**
     * Create a <code>CronTrigger</code>.
     *
     * @param triggerName        String
     * @param startTime          ZonedDateTime
     * @param cronExpression     String
     * @param misFireInstruction int
     * @return CronTrigger
     */
    public CronTrigger createCronTrigger(final String triggerName, final ZonedDateTime startTime, final String cronExpression,
                                         final int misFireInstruction) {
        final CronTriggerFactoryBean factoryBean = new CronTriggerFactoryBean();
        factoryBean.setName(triggerName);
        factoryBean.setStartTime(DateTimeUtils.convertToDate(startTime));
        factoryBean.setCronExpression(cronExpression);
        factoryBean.setTimeZone(TimeZone.getTimeZone(ZoneOffset.UTC));
        factoryBean.setMisfireInstruction(misFireInstruction);
        try {
            factoryBean.afterPropertiesSet();
        } catch (ParseException e) {
            throw new InvalidCronJobException(cronExpression, "expression");
        }
        return factoryBean.getObject();
    }
}
