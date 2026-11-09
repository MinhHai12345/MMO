package com.mmo.module.fb.ingest.adapter.in.web;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.entity.JobState;
import com.mmo.core.cronjob.repository.CronJobRepository;
import com.mmo.core.cronjob.service.CronJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FullDataRebuildControllerTest {

    private CronJobRepository repository;
    private CronJobService cronJobService;
    private FullDataRebuildController controller;

    @BeforeEach
    void setUp() {
        repository = mock(CronJobRepository.class);
        cronJobService = mock(CronJobService.class);
        controller = new FullDataRebuildController(repository, cronJobService);
        ReflectionTestUtils.setField(controller, "configuredApiKey", "test-secret");
    }

    @Test
    void rejectsRequestsWithoutValidAdminKey() {
        ResponseEntity<?> response = controller.start("wrong", new FullDataRebuildController.StartFullRebuildRequest(true));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verifyNoInteractions(repository, cronJobService);
    }

    @Test
    void requiresExplicitConfirmation() {
        ResponseEntity<?> response = controller.start("test-secret",
                new FullDataRebuildController.StartFullRebuildRequest(false));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verifyNoInteractions(repository, cronJobService);
    }

    @Test
    void refusesToStartWhenJobIsAlreadyRunning() {
        CronJob job = new CronJob();
        job.setId(7L);
        job.setJobName("FullDataRebuildJob");
        job.setJobGroup("FBJobGroup");
        job.setJobStatus(JobState.RUNNING);
        when(repository.findByJobNameAndJobGroup("FullDataRebuildJob", "FBJobGroup"))
                .thenReturn(Optional.of(job));

        ResponseEntity<?> response = controller.start("test-secret",
                new FullDataRebuildController.StartFullRebuildRequest(true));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        verifyNoInteractions(cronJobService);
    }

    @Test
    void triggersRegisteredQuartzJobAsynchronously() {
        CronJob job = new CronJob();
        job.setId(7L);
        job.setJobName("FullDataRebuildJob");
        job.setJobGroup("FBJobGroup");
        job.setJobStatus(JobState.SCHEDULED);
        when(repository.findByJobNameAndJobGroup("FullDataRebuildJob", "FBJobGroup"))
                .thenReturn(Optional.of(job));
        when(cronJobService.startJobNow(7L, Map.of())).thenReturn(job);

        ResponseEntity<?> response = controller.start("test-secret",
                new FullDataRebuildController.StartFullRebuildRequest(true));

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        verify(cronJobService).startJobNow(7L, Map.of());
    }

    @Test
    void disablesOperationsWhenAdminKeyIsNotConfigured() {
        ReflectionTestUtils.setField(controller, "configuredApiKey", "");

        ResponseEntity<?> response = controller.status("test-secret");

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        verifyNoInteractions(repository, cronJobService);
    }
}
