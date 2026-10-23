package com.mmo.module.fb.workflow.job;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.job.AbstractJob;
import com.mmo.module.fb.publish.service.impl.ContentPublicationWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

/**
 * CronJob-registry entry point for polling approved and due publication rows.
 * Publishing remains opt-in and is guarded even when the job is triggered manually.
 */
@Slf4j
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class PublicationWorkerQuartzJob extends AbstractJob<CronJob> {

    private final ContentPublicationWorker publicationWorker;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        publicationWorker.processDuePublications();
    }
}
