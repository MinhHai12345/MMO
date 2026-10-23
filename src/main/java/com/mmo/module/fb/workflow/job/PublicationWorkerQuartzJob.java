package com.mmo.module.fb.workflow.job;

import com.mmo.module.fb.publish.service.impl.ContentPublicationWorker;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.quartz.QuartzJobBean;

/**
 * CronJob-registry entry point for polling approved and due publication rows.
 * The worker remains opt-in; the guard also protects manual triggers while disabled.
 */
@Slf4j
@DisallowConcurrentExecution
public class PublicationWorkerQuartzJob extends QuartzJobBean {

    @Autowired
    private ContentPublicationWorker publicationWorker;

    @Value("${app.content.publishing.worker.enabled:false}")
    private boolean enabled;

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        if (!enabled) {
            log.debug("Publication worker is disabled; skipping execution");
            return;
        }

        try {
            publicationWorker.processDuePublications();
        } catch (RuntimeException failure) {
            log.error("Publication worker failed", failure);
            throw new JobExecutionException("Publication worker failed", failure, false);
        }
    }
}
