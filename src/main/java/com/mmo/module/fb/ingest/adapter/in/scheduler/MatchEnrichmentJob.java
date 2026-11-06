package com.mmo.module.fb.ingest.adapter.in.scheduler;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.job.AbstractJob;
import com.mmo.module.fb.ingest.application.port.in.MatchEnrichmentUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

/** Thin Quartz adapter for the match-enrichment application use case. */
@Slf4j
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class MatchEnrichmentJob extends AbstractJob<CronJob> {

    private final MatchEnrichmentUseCase matchEnrichmentUseCase;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        log.info("Starting match enrichment");
        matchEnrichmentUseCase.enrichFinishedMatches();
        log.info("Match enrichment completed");
    }
}
