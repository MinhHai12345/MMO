package com.mmo.config;

import com.mmo.module.fb.workflow.job.ContentWorkflowQuartzJob;
import com.mmo.module.fb.workflow.job.PublicationWorkerQuartzJob;
import org.quartz.JobDetail;
import org.quartz.SimpleTrigger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.CronTriggerFactoryBean;
import org.springframework.scheduling.quartz.JobDetailFactoryBean;
import org.springframework.scheduling.quartz.SimpleTriggerFactoryBean;

import java.util.TimeZone;

@Configuration
public class FbWorkflowQuartzConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "app.content.workflow.scheduler", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public JobDetailFactoryBean contentWorkflowJobDetail() {
        JobDetailFactoryBean factory = new JobDetailFactoryBean();
        factory.setJobClass(ContentWorkflowQuartzJob.class);
        factory.setName("fbContentWorkflowJob");
        factory.setGroup("fb-workflow");
        factory.setDurability(true);
        return factory;
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.content.workflow.scheduler", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public CronTriggerFactoryBean contentWorkflowTrigger(
            @Qualifier("contentWorkflowJobDetail") JobDetail jobDetail,
            @Value("${app.content.workflow.scheduler.cron:0 30 11 * * ?}") String cron) {
        CronTriggerFactoryBean factory = new CronTriggerFactoryBean();
        factory.setJobDetail(jobDetail);
        factory.setName("fbContentWorkflowTrigger");
        factory.setGroup("fb-workflow");
        factory.setCronExpression(toQuartzCron(cron));
        factory.setTimeZone(TimeZone.getTimeZone("UTC"));
        return factory;
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.content.publishing.worker", name = "enabled",
            havingValue = "true", matchIfMissing = false)
    public JobDetailFactoryBean publicationWorkerJobDetail() {
        JobDetailFactoryBean factory = new JobDetailFactoryBean();
        factory.setJobClass(PublicationWorkerQuartzJob.class);
        factory.setName("fbPublicationWorkerJob");
        factory.setGroup("fb-workflow");
        factory.setDurability(true);
        return factory;
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.content.publishing.worker", name = "enabled",
            havingValue = "true", matchIfMissing = false)
    public SimpleTriggerFactoryBean publicationWorkerTrigger(
            @Qualifier("publicationWorkerJobDetail") JobDetail jobDetail,
            @Value("${app.content.publishing.worker.fixed-delay-ms:30000}") long fixedDelayMs,
            @Value("${app.content.publishing.worker.initial-delay-ms:15000}") long initialDelayMs) {
        SimpleTriggerFactoryBean factory = new SimpleTriggerFactoryBean();
        factory.setJobDetail(jobDetail);
        factory.setName("fbPublicationWorkerTrigger");
        factory.setGroup("fb-workflow");
        factory.setStartDelay(Math.max(0L, initialDelayMs));
        factory.setRepeatInterval(Math.max(1_000L, fixedDelayMs));
        factory.setRepeatCount(SimpleTrigger.REPEAT_INDEFINITELY);
        factory.setMisfireInstruction(SimpleTrigger.MISFIRE_INSTRUCTION_RESCHEDULE_NEXT_WITH_REMAINING_COUNT);
        return factory;
    }

    /**
     * Quartz cron requires '?' in either day-of-month or day-of-week. Spring cron
     * expressions commonly use '*' for both; normalize that common six-field form.
     */
    private static String toQuartzCron(String cron) {
        String[] fields = cron == null ? new String[0] : cron.trim().split("\\s+");
        if (fields.length != 6) {
            throw new IllegalArgumentException("Content workflow cron must contain six fields");
        }
        if ("*".equals(fields[3]) && "*".equals(fields[5])) {
            fields[5] = "?";
        }
        return String.join(" ", fields);
    }
}
