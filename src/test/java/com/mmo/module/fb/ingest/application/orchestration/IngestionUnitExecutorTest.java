package com.mmo.module.fb.ingest.application.orchestration;

import com.mmo.module.fb.ingest.application.port.out.ProviderCheckpointPort;
import com.mmo.module.fb.ingest.enums.CheckpointStatus;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.Dataset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IngestionUnitExecutorTest {

    private ProviderCheckpointPort checkpoints;
    private IngestionUnitExecutor executor;
    private IngestionWorkUnit unit;

    @BeforeEach
    void setUp() {
        checkpoints = mock(ProviderCheckpointPort.class);
        executor = new IngestionUnitExecutor(checkpoints);
        unit = new IngestionWorkUnit(Dataset.FIXTURE, 12L, 34L, "2024/2025",
                Provider.FOOTBALL_DATA, IngestionWorkUnit.Role.PRIMARY);
    }

    @Test
    void skipsOnlySatisfiedHistoricalUnit() {
        when(checkpoints.isSatisfied(Provider.FOOTBALL_DATA, Dataset.FIXTURE, 34L, "2024/2025"))
                .thenReturn(true);

        IngestionUnitResult result = executor.execute(unit, true, () -> fail("must not execute"));

        assertTrue(result.skipped());
        verify(checkpoints, never()).markStatus(any(), any(), any(), any(), any());
    }

    @Test
    void activeUnitAlwaysRefreshesEvenWhenCheckpointIsSatisfied() {
        when(checkpoints.isSatisfied(Provider.FOOTBALL_DATA, Dataset.FIXTURE, 34L, "2024/2025"))
                .thenReturn(true);

        IngestionUnitResult result = executor.execute(unit, false, () -> { });

        assertEquals(CheckpointStatus.COMPLETED, result.status());
        verify(checkpoints, never()).isSatisfied(any(), any(), any(), any());
        verify(checkpoints).markStatus(Provider.FOOTBALL_DATA, Dataset.FIXTURE,
                34L, "2024/2025", CheckpointStatus.RUNNING);
        verify(checkpoints).markStatus(Provider.FOOTBALL_DATA, Dataset.FIXTURE,
                34L, "2024/2025", CheckpointStatus.COMPLETED);
    }

    @Test
    void recordsPartialAndFailedOutcomesWithoutThrowing() {
        IngestionUnitResult partial = executor.execute(unit, false,
                () -> { throw new com.mmo.module.fb.ingest.application.exception.IngestionPartialException("partial"); });
        IngestionUnitResult failed = executor.execute(unit, false,
                () -> { throw new IllegalStateException("provider down"); });

        assertEquals(CheckpointStatus.PARTIAL, partial.status());
        assertEquals(CheckpointStatus.FAILED, failed.status());
        verify(checkpoints).markStatus(Provider.FOOTBALL_DATA, Dataset.FIXTURE,
                34L, "2024/2025", CheckpointStatus.PARTIAL);
        verify(checkpoints).markStatus(Provider.FOOTBALL_DATA, Dataset.FIXTURE,
                34L, "2024/2025", CheckpointStatus.FAILED);
    }

    @Test
    void executionReportSeparatesUnavailableFromCompleted() {
        IngestionUnitResult completed = IngestionUnitResult.completed(unit, 12);
        IngestionUnitResult unavailable = IngestionUnitResult.notAvailable(unit, "not published", 4);
        IngestionExecutionReport report = new IngestionExecutionReport(List.of(completed, unavailable));

        assertTrue(report.isSuccessful());
        assertEquals(1, report.completed());
        assertEquals(1, report.unavailable());
        assertEquals(0, report.failed());
    }
}
