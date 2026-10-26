package com.mmo.module.fb.ingest.job;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.job.AbstractJob;
import com.mmo.module.fb.ingest.service.TeamIngestionService;
import com.mmo.module.fb.ingest.enums.Provider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class TeamSyncJob extends AbstractJob<CronJob> {

    private final TeamIngestionService teamIngestionService;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        Provider provider = IngestionJobParameters.provider(context, "team");
        log.info("Starting team synchronization; providerOverride={}", provider);
        if (provider == null) {
            teamIngestionService.syncTeams();
        } else {
            teamIngestionService.syncTeamsForProvider(provider);
        }
        log.info("Team synchronization completed; providerOverride={}", provider);
    }
}
