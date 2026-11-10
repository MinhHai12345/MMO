package com.mmo.module.fb.workflow.application.service;

import com.mmo.module.fb.analytics.service.AdvancedMetricsProcessingService;
import com.mmo.module.fb.analytics.service.MatchPredictionProcessingService;
import com.mmo.module.fb.content.entity.ContentWorkflowRunEntity;
import com.mmo.module.fb.content.repository.ContentWorkflowRunRepository;
import com.mmo.module.fb.content.service.ContentDraftService.DraftFormat;
import com.mmo.module.fb.content.service.ContentLifecycleService;
import com.mmo.module.fb.content.service.ContentWorkflowOrchestrator.WorkflowCommand;
import com.mmo.module.fb.ingest.application.model.ProviderOverrides;
import com.mmo.module.fb.ingest.application.port.in.ActiveSeasonDataSyncUseCase;
import com.mmo.module.fb.workflow.application.port.in.FootballWorkflowUseCase.WorkflowReport;
import com.mmo.module.fb.workflow.domain.enums.FootballWorkflowStage;
import com.mmo.module.fb.workflow.domain.enums.FootballWorkflowStageStatus;
import com.mmo.module.fb.workflow.domain.enums.FootballWorkflowStatus;
import com.mmo.module.fb.workflow.infrastructure.persistence.repository.FootballWorkflowRunRepository;
import com.mmo.module.fb.workflow.infrastructure.persistence.repository.FootballWorkflowStageExecutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({FootballWorkflowCoordinator.class, WorkflowCheckpointService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "MMO_MYSQL_TEST_URL", matches = "^jdbc:mysql:.*$")
class FootballWorkflowCoordinatorMySqlIntegrationTest {

    @Autowired
    private FootballWorkflowCoordinator coordinator;

    @Autowired
    private WorkflowCheckpointService checkpointService;

    @Autowired
    private FootballWorkflowRunRepository runRepository;

    @Autowired
    private FootballWorkflowStageExecutionRepository stageRepository;

    @MockBean
    private ActiveSeasonDataSyncUseCase activeSeasonDataSyncUseCase;

    @MockBean
    private DataReadinessGateService readinessGateService;

    @MockBean
    private AdvancedMetricsProcessingService advancedMetricsProcessingService;

    @MockBean
    private MatchPredictionProcessingService matchPredictionProcessingService;

    @MockBean
    private ContentLifecycleService contentLifecycleService;

    @MockBean
    private ContentWorkflowRunRepository contentWorkflowRunRepository;

    @DynamicPropertySource
    static void configureMySql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> requiredEnvironmentValue("MMO_MYSQL_TEST_URL"));
        registry.add("spring.datasource.username", () -> requiredEnvironmentValue("MMO_MYSQL_TEST_USERNAME"));
        registry.add("spring.datasource.password", () -> requiredEnvironmentValue("MMO_MYSQL_TEST_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @BeforeEach
    void cleanWorkflowTables() {
        stageRepository.deleteAllInBatch();
        runRepository.deleteAllInBatch();
        configureSuccessfulDownstreamStages();
    }

    @Test
    void shouldExecuteAllStagesInOrderAndPersistCompletedCheckpoints() {
        LocalDate date = LocalDate.of(2026, 11, 10);

        WorkflowReport report = coordinator.run(date, () -> false);

        assertEquals("COMPLETED", report.status());
        assertEquals(List.of("DATA_INGESTION", "DATA_READINESS", "ADVANCED_METRICS",
                "PREDICTIONS", "PRE_MATCH_CONTENT"),
                report.steps().stream().map(step -> step.name()).toList());

        InOrder order = inOrder(activeSeasonDataSyncUseCase, readinessGateService,
                advancedMetricsProcessingService, matchPredictionProcessingService, contentLifecycleService);
        order.verify(activeSeasonDataSyncUseCase).sync(any(ProviderOverrides.class), isNull());
        order.verify(readinessGateService).evaluate();
        order.verify(advancedMetricsProcessingService).process(any(BooleanSupplier.class));
        order.verify(matchPredictionProcessingService).processUpcomingMatches(any(BooleanSupplier.class));
        order.verify(contentLifecycleService).generate(any(WorkflowCommand.class), eq("scheduled-content-" + date));

        var run = runRepository.findByIdempotencyKey("football-workflow-" + date).orElseThrow();
        assertEquals(FootballWorkflowStatus.COMPLETED, run.getStatus());
        for (FootballWorkflowStage stage : FootballWorkflowStage.values()) {
            assertEquals(FootballWorkflowStageStatus.COMPLETED,
                    stageRepository.findByWorkflowRunIdAndStage(run.getId(), stage).orElseThrow().getStatus());
        }
        assertEquals("COMPLETED", coordinator.run(date, () -> false).status());
        verifyNoMoreInteractions(activeSeasonDataSyncUseCase, readinessGateService,
                advancedMetricsProcessingService, matchPredictionProcessingService, contentLifecycleService);
    }

    @Test
    void shouldBlockAnalyticsAndContentWhenReadinessGateRejectsData() {
        LocalDate date = LocalDate.of(2026, 11, 11);
        when(readinessGateService.evaluate()).thenReturn(
                new DataReadinessGateService.ReadinessReport(false, Instant.now(), List.of()));

        WorkflowReport report = coordinator.run(date, () -> false);

        assertEquals("FAILED", report.status());
        assertEquals("FAILED", report.steps().get(1).status());
        verify(activeSeasonDataSyncUseCase).sync(any(ProviderOverrides.class), isNull());
        verify(readinessGateService).evaluate();
        verifyNoInteractions(advancedMetricsProcessingService, matchPredictionProcessingService,
                contentLifecycleService);

        var run = runRepository.findByIdempotencyKey("football-workflow-" + date).orElseThrow();
        assertEquals(FootballWorkflowStatus.FAILED, run.getStatus());
        assertEquals(FootballWorkflowStageStatus.COMPLETED,
                stageRepository.findByWorkflowRunIdAndStage(run.getId(), FootballWorkflowStage.DATA_INGESTION)
                        .orElseThrow().getStatus());
        assertEquals(FootballWorkflowStageStatus.FAILED,
                stageRepository.findByWorkflowRunIdAndStage(run.getId(), FootballWorkflowStage.DATA_READINESS)
                        .orElseThrow().getStatus());
        assertTrue(stageRepository.findByWorkflowRunIdAndStage(run.getId(),
                FootballWorkflowStage.ADVANCED_METRICS).isEmpty());
    }

    @Test
    void shouldResumeFromFailedStageWithoutRepeatingCompletedStages() {
        LocalDate date = LocalDate.of(2026, 11, 12);
        when(advancedMetricsProcessingService.process(any(BooleanSupplier.class)))
                .thenThrow(new IllegalStateException("temporary analytics interruption"))
                .thenReturn(new AdvancedMetricsProcessingService.AdvancedMetricsRunReport(
                        "COMPLETED", 4, 2, false, Instant.now(), Instant.now()));

        WorkflowReport firstAttempt = coordinator.run(date, () -> false);
        assertEquals("FAILED", firstAttempt.status());

        var failedRun = runRepository.findByIdempotencyKey("football-workflow-" + date).orElseThrow();
        assertEquals(FootballWorkflowStatus.FAILED, failedRun.getStatus());
        assertEquals(FootballWorkflowStageStatus.COMPLETED,
                stageRepository.findByWorkflowRunIdAndStage(failedRun.getId(), FootballWorkflowStage.DATA_INGESTION)
                        .orElseThrow().getStatus());
        assertEquals(FootballWorkflowStageStatus.COMPLETED,
                stageRepository.findByWorkflowRunIdAndStage(failedRun.getId(), FootballWorkflowStage.DATA_READINESS)
                        .orElseThrow().getStatus());

        WorkflowReport resumedAttempt = coordinator.run(date, () -> false);

        assertEquals("COMPLETED", resumedAttempt.status());
        verify(activeSeasonDataSyncUseCase, times(1)).sync(any(ProviderOverrides.class), isNull());
        verify(readinessGateService, times(1)).evaluate();
        verify(advancedMetricsProcessingService, times(2)).process(any(BooleanSupplier.class));
        verify(matchPredictionProcessingService, times(1))
                .processUpcomingMatches(any(BooleanSupplier.class));
        verify(contentLifecycleService, times(1))
                .generate(any(WorkflowCommand.class), eq("scheduled-content-" + date));

        var completedRun = runRepository.findByIdempotencyKey("football-workflow-" + date).orElseThrow();
        assertEquals(FootballWorkflowStatus.COMPLETED, completedRun.getStatus());
        for (FootballWorkflowStage stage : FootballWorkflowStage.values()) {
            assertEquals(FootballWorkflowStageStatus.COMPLETED,
                    stageRepository.findByWorkflowRunIdAndStage(completedRun.getId(), stage)
                            .orElseThrow().getStatus());
        }
    }

    private void configureSuccessfulDownstreamStages() {
        when(readinessGateService.evaluate()).thenReturn(
                new DataReadinessGateService.ReadinessReport(true, Instant.now(), List.of()));
        when(advancedMetricsProcessingService.process(any(BooleanSupplier.class))).thenReturn(
                new AdvancedMetricsProcessingService.AdvancedMetricsRunReport(
                        "COMPLETED", 3, 2, false, Instant.now(), Instant.now()));
        when(matchPredictionProcessingService.processUpcomingMatches(any(BooleanSupplier.class))).thenReturn(
                new MatchPredictionProcessingService.PredictionRunReport(
                        5, 5, 0, 0, false, Instant.now(), Instant.now()));
        when(contentLifecycleService.generate(any(WorkflowCommand.class), anyString()))
                .thenAnswer(invocation -> contentRun(invocation.getArgument(1)));
    }

    private ContentWorkflowRunEntity contentRun(String idempotencyKey) {
        ContentWorkflowRunEntity run = new ContentWorkflowRunEntity();
        run.setId(900L);
        run.setIdempotencyKey(idempotencyKey);
        run.setStatus("COMPLETED");
        run.setFormat(DraftFormat.PRE_MATCH);
        run.setStartedAt(Instant.now());
        run.setDraftsGenerated(5);
        run.setFailures(0);
        return run;
    }

    private static String requiredEnvironmentValue(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required MySQL integration-test environment variable is missing: " + name);
        }
        return value;
    }
}
