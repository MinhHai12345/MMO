package com.mmo.module.fb.ingest.adapter.in.scheduler;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.job.AbstractJob;
import com.mmo.module.fb.ingest.application.port.in.MatchEnrichmentService;
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

    private final MatchEnrichmentService matchEnrichmentService;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        log.info("Starting match enrichment");
        matchEnrichmentService.enrichFinishedMatches();
        log.info("Match enrichment completed");
    }
}
