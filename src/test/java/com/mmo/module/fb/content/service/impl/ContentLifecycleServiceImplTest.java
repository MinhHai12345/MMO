package com.mmo.module.fb.content.service.impl;

import com.mmo.module.fb.analytics.service.ContentDraftService.DraftFormat;
import com.mmo.module.fb.content.repository.ContentDraftRecordRepository;
import com.mmo.module.fb.content.repository.ContentDraftVersionRepository;
import com.mmo.module.fb.content.repository.ContentWorkflowItemRepository;
import com.mmo.module.fb.content.entity.ContentWorkflowRunEntity;
import com.mmo.module.fb.content.repository.ContentWorkflowRunRepository;
import com.mmo.module.fb.content.service.ContentWorkflowOrchestrator;
import com.mmo.module.fb.content.service.ContentWorkflowOrchestrator.WorkflowCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentLifecycleServiceImplTest {

    @Mock private ContentWorkflowOrchestrator orchestrator;
    @Mock private ContentWorkflowPersistenceService persistenceService;
    @Mock private ContentWorkflowRunRepository runRepository;
    @Mock private ContentDraftRecordRepository draftRepository;
    @Mock private ContentDraftVersionRepository versionRepository;
    @Mock private ContentWorkflowItemRepository itemRepository;

    @InjectMocks private ContentLifecycleServiceImpl service;

    @Test
    void reusesExistingRunForSameIdempotencyKeyAndRequest() {
        Instant from = Instant.parse("2026-10-22T00:00:00Z");
        Instant to = from.plusSeconds(86400);
        WorkflowCommand command = new WorkflowCommand(from, to, 39L, 20, DraftFormat.PRE_MATCH);
        ContentWorkflowRunEntity existing = run(from, to, 39L, 20, DraftFormat.PRE_MATCH);
        when(runRepository.findByIdempotencyKey("request-1")).thenReturn(Optional.of(existing));

        assertSame(existing, service.generate(command, "request-1"));
        verify(orchestrator, never()).generate(command);
        verify(persistenceService, never()).reserve(command, "request-1");
    }

    @Test
    void rejectsReusingIdempotencyKeyWithDifferentFilters() {
        Instant from = Instant.parse("2026-10-22T00:00:00Z");
        WorkflowCommand command = new WorkflowCommand(from, from.plusSeconds(86400), 40L, 20, DraftFormat.PRE_MATCH);
        ContentWorkflowRunEntity existing = run(from, from.plusSeconds(86400), 39L, 20, DraftFormat.PRE_MATCH);
        when(runRepository.findByIdempotencyKey("request-2")).thenReturn(Optional.of(existing));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.generate(command, "request-2"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(orchestrator, never()).generate(command);
    }

    private static ContentWorkflowRunEntity run(Instant from, Instant to, Long leagueId, int limit, DraftFormat format) {
        ContentWorkflowRunEntity run = new ContentWorkflowRunEntity();
        run.setFilterFrom(from);
        run.setFilterTo(to);
        run.setLeagueId(leagueId);
        run.setRequestedLimit(limit);
        run.setFormat(format);
        return run;
    }
}
