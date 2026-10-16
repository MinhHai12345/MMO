package com.mmo.module.fb.ingest.job;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.job.AbstractJob;
import com.mmo.module.fb.analytics.service.WalkForwardBackTestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class WalkForwardBackTestJob extends AbstractJob<CronJob> {

    private final WalkForwardBackTestService backTestService;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        Instant to = Instant.now();
        Instant from = to.minus(180, ChronoUnit.DAYS);
        log.info("Starting leakage-safe walk-forward backtest: from={}, to={}", from, to);
        WalkForwardBackTestService.BackTestReport report = backTestService.run(from, to);
        log.info("Walk-forward report: testedMatches={}, calibratedMatches={}, status={}, raw={}, calibrated={}",
                report.testedMatches(), report.calibratedMatches(), report.status(),
                report.raw(), report.calibrated());
    }
}
