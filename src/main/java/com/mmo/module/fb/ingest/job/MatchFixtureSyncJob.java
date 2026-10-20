package com.mmo.module.fb.ingest.job;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.job.AbstractJob;
import com.mmo.module.fb.ingest.service.FixtureIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;

@Slf4j
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class MatchFixtureSyncJob extends AbstractJob<CronJob> {

    private final FixtureIngestionService fixtureIngestionService;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate to = today.plusDays(14);
        log.info("Starting upcoming fixture refresh for {} to {}", today, to);
        fixtureIngestionService.syncCurrentSeasonFixtures(today, to);
        log.info("Upcoming fixture refresh completed for {} to {}", today, to);
    }
}
