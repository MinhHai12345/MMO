package com.mmo.module.fb.workflow.job;

import com.mmo.module.fb.publish.service.impl.ContentPublicationWorker;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

/** Quartz polling job for approved and due publication rows. */
@Slf4j
@DisallowConcurrentExecution
public class PublicationWorkerQuartzJob extends QuartzJobBean {

    @Autowired
    private ContentPublicationWorker publicationWorker;

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        try {
            publicationWorker.processDuePublications();
        } catch (RuntimeException failure) {
            log.error("Quartz publication worker failed", failure);
            throw new JobExecutionException("Publication worker failed", failure, false);
        }
    }
}
