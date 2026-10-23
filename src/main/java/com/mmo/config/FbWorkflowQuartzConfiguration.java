package com.mmo.config;

import com.mmo.module.fb.workflow.job.PublicationWorkerQuartzJob;
import org.quartz.JobDetail;
import org.quartz.SimpleTrigger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.JobDetailFactoryBean;
import org.springframework.scheduling.quartz.SimpleTriggerFactoryBean;

/**
 * Quartz configuration for the publication polling worker.
 *
 * Content workflow scheduling is registered through the existing persisted CronJob
 * registry in FBCronjobInitial, so the pipeline can reuse existing FB jobs.
 */
@Configuration
public class FbWorkflowQuartzConfiguration {

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
}
