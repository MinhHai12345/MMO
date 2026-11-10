package com.mmo.module.fb.initial;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.repository.CronJobRepository;
import com.mmo.core.cronjob.service.CronJobService;
import com.mmo.module.fb.workflow.job.FootballWorkflowOrchestrationQuartzJob;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FootballQuartzJobInitializerTest {

    @Test
    void registersDurableWorkflowAsManualOnlyQuartzJob() {
        CronJobRepository repository = mock(CronJobRepository.class);
        CronJobService service = mock(CronJobService.class);
        when(repository.findByJobNameAndJobGroup(anyString(), eq("FBJobGroup")))
                .thenReturn(Optional.empty());

        new FootballQuartzJobInitializer(repository, service).initialize();

        ArgumentCaptor<CronJob> captor = ArgumentCaptor.forClass(CronJob.class);
        verify(service, org.mockito.Mockito.atLeastOnce()).saveJob(captor.capture());

        CronJob workflowJob = captor.getAllValues().stream()
                .filter(job -> FootballWorkflowOrchestrationQuartzJob.class.getName().equals(job.getJobClass()))
                .findFirst()
                .orElseThrow();

        assertEquals("FootballWorkflowOrchestrationQuartzJob", workflowJob.getJobName());
        assertEquals("0 0 0 1 1 ? 2099", workflowJob.getCronExpression());
        assertTrue(workflowJob.getDescription().contains("Manual-only"));
    }
}
