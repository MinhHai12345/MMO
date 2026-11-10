package com.mmo.module.fb.workflow.application.service;

import com.mmo.module.fb.analytics.service.AdvancedMetricsProcessingService;
import com.mmo.module.fb.analytics.service.MatchPredictionProcessingService;
import com.mmo.module.fb.content.entity.ContentWorkflowRunEntity;
import com.mmo.module.fb.content.repository.ContentWorkflowRunRepository;
import com.mmo.module.fb.content.service.ContentLifecycleService;
import com.mmo.module.fb.ingest.application.port.in.FullDataRebuildUseCase;
import com.mmo.module.fb.workflow.domain.enums.FootballWorkflowStage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FootballWorkflowCoordinatorTest {

    @Mock private FullDataRebuildUseCase fullDataRebuildUseCase;
    @Mock private AdvancedMetricsProcessingService advancedMetricsProcessingService;
    @Mock private MatchPredictionProcessingService matchPredictionProcessingService;
    @Mock private ContentLifecycleService contentLifecycleService;
    @Mock private WorkflowCheckpointService checkpointService;
    @Mock private DataReadinessGateService readinessGateService;
    @Mock private ContentWorkflowRunRepository contentWorkflowRunRepository;

    @InjectMocks
    private FootballWorkflowCoordinator coordinator;

    @BeforeEach
    void setUp() {
        when(checkpointService.getOrCreateRun(any())).thenReturn(101L);
        when(checkpointService.isRunCompleted(101L)).thenReturn(false);
        when(checkpointService.claimRun(101L)).thenReturn("test-execution-token");
        when(checkpointService.isStageCompleted(eq(101L), any(FootballWorkflowStage.class))).thenReturn(false);
        lenient().when(readinessGateService.evaluate()).thenReturn(
                new DataReadinessGateService.ReadinessReport(true, Instant.now(), List.of()));
    }

    @Test
    void executesStagesInDependencyOrderAndReusesDailyContentIdempotencyKey() {
        LocalDate date = LocalDate.of(2026, 11, 10);
        when(advancedMetricsProcessingService.process(any()))
                .thenReturn(new AdvancedMetricsProcessingService.AdvancedMetricsRunReport(
                        "COMPLETED", 20, 30, false, Instant.now(), Instant.now()));
        when(matchPredictionProcessingService.processUpcomingMatches(any()))
                .thenReturn(new MatchPredictionProcessingService.PredictionRunReport(
                        5, 5, 0, 0, false, Instant.now(), Instant.now()));
        ContentWorkflowRunEntity contentRun = org.mockito.Mockito.mock(ContentWorkflowRunEntity.class);
        when(contentRun.getId()).thenReturn(123L);
        when(contentRun.getDraftsGenerated()).thenReturn(5);
        when(contentRun.getFailures()).thenReturn(0);
        when(contentLifecycleService.generate(any(), eq("scheduled-content-" + date))).thenReturn(contentRun);

        var report = coordinator.run(date, () -> false);

        assertEquals("COMPLETED", report.status());
        assertEquals(5, report.steps().size());
        assertEquals(123L, report.contentRunId());
        assertEquals(5, report.draftsGenerated());

        InOrder order = inOrder(fullDataRebuildUseCase, readinessGateService,
                advancedMetricsProcessingService, matchPredictionProcessingService, contentLifecycleService);
        order.verify(fullDataRebuildUseCase).rebuildAllData();
        order.verify(readinessGateService).evaluate();
        order.verify(advancedMetricsProcessingService).process(any());
        order.verify(matchPredictionProcessingService).processUpcomingMatches(any());
        order.verify(contentLifecycleService).generate(any(), eq("scheduled-content-" + date));
        verify(checkpointService).completeRun(101L, "test-execution-token");
    }

    @Test
    void stopsDownstreamStagesWhenIngestionFails() {
        doThrow(new IllegalStateException("ingestion failed"))
                .when(fullDataRebuildUseCase).rebuildAllData();

        var report = coordinator.run(LocalDate.of(2026, 11, 10), () -> false);

        assertEquals("FAILED", report.status());
        assertEquals(1, report.steps().size());
        assertEquals("FAILED", report.steps().get(0).status());
        verify(readinessGateService, never()).evaluate();
        verify(advancedMetricsProcessingService, never()).process(any());
        verify(matchPredictionProcessingService, never()).processUpcomingMatches(any());
        verify(contentLifecycleService, never()).generate(any(), any());
    }

    @Test
    void stopsBeforeAnyStageWhenCancellationIsAlreadyRequested() {
        var report = coordinator.run(LocalDate.of(2026, 11, 10), () -> true);

        assertEquals("INTERRUPTED", report.status());
        assertEquals(0, report.steps().size());
        assertNull(report.contentRunId());
        verify(fullDataRebuildUseCase, never()).rebuildAllData();
        verify(advancedMetricsProcessingService, never()).process(any());
        verify(checkpointService).releaseRun(101L, "test-execution-token");
    }

    @Test
    void stopsBeforeContentWhenPredictionProcessingHasFailures() {
        when(advancedMetricsProcessingService.process(any()))
                .thenReturn(new AdvancedMetricsProcessingService.AdvancedMetricsRunReport(
                        "COMPLETED", 1, 1, false, Instant.now(), Instant.now()));
        when(matchPredictionProcessingService.processUpcomingMatches(any()))
                .thenReturn(new MatchPredictionProcessingService.PredictionRunReport(
                        3, 2, 0, 1, false, Instant.now(), Instant.now()));

        var report = coordinator.run(LocalDate.of(2026, 11, 10), () -> false);

        assertEquals("FAILED", report.status());
        assertEquals(4, report.steps().size());
        verify(contentLifecycleService, never()).generate(any(), any());
    }
}
