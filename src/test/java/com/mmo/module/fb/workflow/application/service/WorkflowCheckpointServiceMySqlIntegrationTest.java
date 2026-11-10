package com.mmo.module.fb.workflow.application.service;

import com.mmo.module.fb.workflow.domain.entity.FootballWorkflowRunEntity;
import com.mmo.module.fb.workflow.domain.entity.FootballWorkflowStageExecutionEntity;
import com.mmo.module.fb.workflow.domain.enums.FootballWorkflowStage;
import com.mmo.module.fb.workflow.domain.enums.FootballWorkflowStageStatus;
import com.mmo.module.fb.workflow.domain.enums.FootballWorkflowStatus;
import com.mmo.module.fb.workflow.infrastructure.persistence.repository.FootballWorkflowRunRepository;
import com.mmo.module.fb.workflow.infrastructure.persistence.repository.FootballWorkflowStageExecutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(WorkflowCheckpointService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "MMO_MYSQL_TEST_URL", matches = "^jdbc:mysql:.*$")
class WorkflowCheckpointServiceMySqlIntegrationTest {

    @Autowired
    private WorkflowCheckpointService checkpointService;

    @Autowired
    private FootballWorkflowRunRepository runRepository;

    @Autowired
    private FootballWorkflowStageExecutionRepository stageRepository;

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
    }

    @Test
    void shouldCreateRunIdempotentlyAndRejectSecondOwnerWhileLeaseIsActive() {
        String idempotencyKey = uniqueKey();
        Long firstRunId = checkpointService.getOrCreateRun(idempotencyKey);
        Long secondRunId = checkpointService.getOrCreateRun(idempotencyKey);

        assertEquals(firstRunId, secondRunId);

        String firstToken = checkpointService.claimRun(firstRunId);
        assertNotNull(firstToken);
        assertNull(checkpointService.claimRun(firstRunId));

        FootballWorkflowRunEntity persistedRun = runRepository.findById(firstRunId).orElseThrow();
        assertEquals(FootballWorkflowStatus.RUNNING, persistedRun.getStatus());
        assertEquals(firstToken, persistedRun.getExecutionToken());
        assertTrue(persistedRun.getLeaseUntil().isAfter(Instant.now()));
    }

    @Test
    void shouldAllowOnlyOneConcurrentNodeToClaimTheSameRun() throws Exception {
        Long runId = checkpointService.getOrCreateRun(uniqueKey());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<String> firstClaim = workers.submit(() -> claimAfterBarrier(runId, ready, start));
            Future<String> secondClaim = workers.submit(() -> claimAfterBarrier(runId, ready, start));

            assertTrue(ready.await(10, TimeUnit.SECONDS), "Both coordinator workers should be ready");
            start.countDown();

            String firstToken = firstClaim.get(20, TimeUnit.SECONDS);
            String secondToken = secondClaim.get(20, TimeUnit.SECONDS);

            assertNotEquals(firstToken == null, secondToken == null,
                    "Exactly one coordinator must acquire the execution lease");
            String ownerToken = firstToken == null ? secondToken : firstToken;
            FootballWorkflowRunEntity persistedRun = runRepository.findById(runId).orElseThrow();
            assertEquals(FootballWorkflowStatus.RUNNING, persistedRun.getStatus());
            assertEquals(ownerToken, persistedRun.getExecutionToken());
            assertTrue(persistedRun.getLeaseUntil().isAfter(Instant.now()));
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void shouldRejectStaleOwnerReleaseAfterAnotherNodeTakesOver() {
        Long runId = checkpointService.getOrCreateRun(uniqueKey());
        String previousToken = checkpointService.claimRun(runId);
        checkpointService.startStage(runId, FootballWorkflowStage.DATA_INGESTION, previousToken);
        expireLease(runId);

        String currentToken = checkpointService.claimRun(runId);
        assertNotNull(currentToken);
        assertNotEquals(previousToken, currentToken);

        assertThrows(IllegalStateException.class,
                () -> checkpointService.releaseRun(runId, previousToken));

        FootballWorkflowRunEntity persistedRun = runRepository.findById(runId).orElseThrow();
        assertEquals(FootballWorkflowStatus.RUNNING, persistedRun.getStatus());
        assertEquals(currentToken, persistedRun.getExecutionToken());
        assertTrue(persistedRun.getLeaseUntil().isAfter(Instant.now()));
    }

    @Test
    void shouldNotAllowACompletedRunToBeClaimedAgain() {
        Long runId = checkpointService.getOrCreateRun(uniqueKey());
        String token = checkpointService.claimRun(runId);

        checkpointService.startStage(runId, FootballWorkflowStage.DATA_INGESTION, token);
        checkpointService.completeStage(runId, FootballWorkflowStage.DATA_INGESTION, token);
        checkpointService.completeRun(runId, token);

        assertNull(checkpointService.claimRun(runId));
        FootballWorkflowRunEntity persistedRun = runRepository.findById(runId).orElseThrow();
        assertEquals(FootballWorkflowStatus.COMPLETED, persistedRun.getStatus());
        assertNull(persistedRun.getExecutionToken());
        assertNull(persistedRun.getLeaseUntil());
    }

    private String claimAfterBarrier(Long runId, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for concurrent claim barrier");
        }
        return checkpointService.claimRun(runId);
    }

    @Test
    void shouldResumeAfterLeaseExpiryAndFenceThePreviousExecutionToken() {
        Long runId = checkpointService.getOrCreateRun(uniqueKey());
        String previousToken = checkpointService.claimRun(runId);

        checkpointService.startStage(runId, FootballWorkflowStage.DATA_INGESTION, previousToken);
        checkpointService.completeStage(runId, FootballWorkflowStage.DATA_INGESTION, previousToken);

        checkpointService.startStage(runId, FootballWorkflowStage.DATA_READINESS, previousToken);
        expireLease(runId);

        String resumedToken = checkpointService.claimRun(runId);

        assertNotNull(resumedToken);
        assertNotEquals(previousToken, resumedToken);
        assertTrue(checkpointService.isStageCompleted(runId, FootballWorkflowStage.DATA_INGESTION));
        assertFalse(checkpointService.isStageCompleted(runId, FootballWorkflowStage.DATA_READINESS));

        assertThrows(IllegalStateException.class,
                () -> checkpointService.completeStage(
                        runId, FootballWorkflowStage.DATA_READINESS, previousToken));

        checkpointService.startStage(runId, FootballWorkflowStage.DATA_READINESS, resumedToken);
        checkpointService.completeStage(runId, FootballWorkflowStage.DATA_READINESS, resumedToken);

        FootballWorkflowStageExecutionEntity resumedStage = stageRepository
                .findByWorkflowRunIdAndStage(runId, FootballWorkflowStage.DATA_READINESS)
                .orElseThrow();
        assertEquals(FootballWorkflowStageStatus.COMPLETED, resumedStage.getStatus());
    }

    @Test
    void shouldReleaseInterruptedStageWithoutDiscardingCompletedCheckpoint() {
        Long runId = checkpointService.getOrCreateRun(uniqueKey());
        String token = checkpointService.claimRun(runId);

        checkpointService.startStage(runId, FootballWorkflowStage.DATA_INGESTION, token);
        checkpointService.completeStage(runId, FootballWorkflowStage.DATA_INGESTION, token);
        checkpointService.startStage(runId, FootballWorkflowStage.DATA_READINESS, token);

        checkpointService.releaseRun(runId, token);

        FootballWorkflowRunEntity releasedRun = runRepository.findById(runId).orElseThrow();
        assertEquals(FootballWorkflowStatus.PENDING, releasedRun.getStatus());
        assertNull(releasedRun.getExecutionToken());
        assertNull(releasedRun.getLeaseUntil());
        assertTrue(checkpointService.isStageCompleted(runId, FootballWorkflowStage.DATA_INGESTION));

        FootballWorkflowStageExecutionEntity interruptedStage = stageRepository
                .findByWorkflowRunIdAndStage(runId, FootballWorkflowStage.DATA_READINESS)
                .orElseThrow();
        assertEquals(FootballWorkflowStageStatus.PENDING, interruptedStage.getStatus());
        assertNotNull(interruptedStage.getFailureReason());

        assertNotNull(checkpointService.claimRun(runId));
    }

    private void expireLease(Long runId) {
        FootballWorkflowRunEntity run = runRepository.findById(runId).orElseThrow();
        run.setLeaseUntil(Instant.now().minusSeconds(1));
        runRepository.saveAndFlush(run);
    }

    private String uniqueKey() {
        return "workflow-integration-" + UUID.randomUUID();
    }

    private static String requiredEnvironmentValue(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required MySQL integration-test environment variable is missing: " + name);
        }
        return value;
    }
}
