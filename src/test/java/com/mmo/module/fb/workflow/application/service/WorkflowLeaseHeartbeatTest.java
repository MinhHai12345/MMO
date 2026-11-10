package com.mmo.module.fb.workflow.application.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class WorkflowLeaseHeartbeatTest {

    private final WorkflowCheckpointService checkpointService = mock(WorkflowCheckpointService.class);
    private final WorkflowLeaseHeartbeat heartbeat =
            new WorkflowLeaseHeartbeat(checkpointService, Duration.ofMillis(10));

    @AfterEach
    void shutDownHeartbeatScheduler() {
        heartbeat.shutdown();
    }

    @Test
    void shouldRenewLeasePeriodicallyWhileHeartbeatIsActive() {
        try (WorkflowLeaseHeartbeat.Heartbeat active = heartbeat.start(42L, "execution-token")) {
            verify(checkpointService, timeout(1_000).atLeastOnce())
                    .renewLease(42L, "execution-token");
            active.assertHealthy();
        }
    }

    @Test
    void shouldMarkHeartbeatUnhealthyWhenLeaseRenewalFails() {
        when(checkpointService.renewLease(42L, "execution-token"))
                .thenThrow(new IllegalStateException("Lease ownership was lost."));

        try (WorkflowLeaseHeartbeat.Heartbeat active = heartbeat.start(42L, "execution-token")) {
            verify(checkpointService, timeout(1_000).atLeastOnce())
                    .renewLease(42L, "execution-token");
            assertThrows(IllegalStateException.class, active::assertHealthy);
        }
    }
}
