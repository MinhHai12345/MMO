package com.mmo.module.fb.content.service.impl;

import com.mmo.module.fb.content.persistence.ContentDraftRecordRepository;
import com.mmo.module.fb.content.persistence.ContentDraftVersionRepository;
import com.mmo.module.fb.content.persistence.ContentWorkflowItemRepository;
import com.mmo.module.fb.content.persistence.ContentWorkflowRunEntity;
import com.mmo.module.fb.content.persistence.ContentWorkflowRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ContentWorkflowLeaseTest {

    @Mock
    private ContentWorkflowRunRepository runRepository;
    @Mock
    private ContentWorkflowItemRepository itemRepository;
    @Mock
    private ContentDraftRecordRepository draftRepository;
    @Mock
    private ContentDraftVersionRepository versionRepository;

    @InjectMocks
    private ContentWorkflowPersistenceService persistenceService;

    @BeforeEach
    void setUp() {
        lenient().when(runRepository.save(any(ContentWorkflowRunEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(itemRepository.findByWorkflowRunIdOrderByIdAsc(any()))
                .thenReturn(List.of());
    }

    @Test
    void shouldRejectResumeWhenLeaseIsStillActive() {
        ContentWorkflowRunEntity run = runningRun(Instant.now().plus(5, ChronoUnit.MINUTES));
        when(runRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(run));

        assertThrows(ResponseStatusException.class, () -> persistenceService.claimForResume(10L));

        verify(runRepository, never()).save(any());
    }

    @Test
    void shouldReclaimExpiredRunningWorkflowWithNewExecutionToken() {
        ContentWorkflowRunEntity run = runningRun(Instant.now().minus(1, ChronoUnit.SECONDS));
        String oldToken = run.getExecutionToken();
        when(runRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(run));

        ContentWorkflowRunEntity claimed = persistenceService.claimForResume(10L);

        assertEquals("RUNNING", claimed.getStatus());
        assertNotNull(claimed.getExecutionToken());
        assertNotEquals(oldToken, claimed.getExecutionToken());
        assertTrue(claimed.getLeaseUntil().isAfter(Instant.now()));
        assertNotNull(claimed.getHeartbeatAt());
        assertNull(claimed.getCompletedAt());
        verify(runRepository).save(run);
    }

    @Test
    void shouldAllowRetryOfFailedWorkflowAndAcquireLease() {
        ContentWorkflowRunEntity run = new ContentWorkflowRunEntity();
        run.setStatus("FAILED");
        run.setExecutionToken("old-token");
        run.setCompletedAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(runRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(run));

        ContentWorkflowRunEntity claimed = persistenceService.claimForResume(11L);

        assertEquals("RUNNING", claimed.getStatus());
        assertNotNull(claimed.getExecutionToken());
        assertNotEquals("old-token", claimed.getExecutionToken());
        assertNotNull(claimed.getLeaseUntil());
        assertNull(claimed.getCompletedAt());
    }

    @Test
    void shouldUseStartedAtAsFallbackForLegacyRunWithoutLeaseMetadata() {
        ContentWorkflowRunEntity run = new ContentWorkflowRunEntity();
        run.setStatus("RUNNING");
        run.setStartedAt(Instant.now().minus(20, ChronoUnit.MINUTES));
        when(runRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(run));

        ContentWorkflowRunEntity claimed = persistenceService.claimForResume(12L);

        assertEquals("RUNNING", claimed.getStatus());
        assertNotNull(claimed.getExecutionToken());
        assertTrue(claimed.getLeaseUntil().isAfter(Instant.now()));
    }

    private ContentWorkflowRunEntity runningRun(Instant leaseUntil) {
        ContentWorkflowRunEntity run = new ContentWorkflowRunEntity();
        run.setStatus("RUNNING");
        run.setExecutionToken("old-token");
        run.setHeartbeatAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        run.setStartedAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        run.setLeaseUntil(leaseUntil);
        return run;
    }
}
