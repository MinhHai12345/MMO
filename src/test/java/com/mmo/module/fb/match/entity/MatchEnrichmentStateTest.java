package com.mmo.module.fb.match.entity;

import com.mmo.module.fb.domain.match.entity.MatchEnrichmentState;
import com.mmo.module.fb.domain.match.enums.MatchProvider;
import com.mmo.module.fb.domain.match.enums.MatchEnrichmentResource;
import com.mmo.module.fb.domain.match.enums.MatchEnrichmentStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class MatchEnrichmentStateTest {

    @Test
    void tracksIndependentProviderResourceAttemptLifecycle() {
        Instant now = Instant.parse("2026-11-04T10:00:00Z");
        MatchEnrichmentState state = MatchEnrichmentState.builder()
                .provider(MatchProvider.SOFA_SCORE)
                .resource(MatchEnrichmentResource.TEAM_STATISTICS)
                .build();

        state.markAttempt(now);
        assertEquals(MatchEnrichmentStatus.IN_PROGRESS, state.getStatus());
        assertEquals(1, state.getAttemptCount());
        assertEquals(now, state.getLastAttemptAt());

        Instant completed = now.plusSeconds(10);
        state.markSucceeded(completed);
        assertEquals(MatchEnrichmentStatus.SUCCEEDED, state.getStatus());
        assertEquals(completed, state.getLastSuccessAt());
        assertNull(state.getNextRetryAt());
    }

    @Test
    void recordsEmptyAndSkippedAsDifferentOutcomes() {
        Instant now = Instant.parse("2026-11-04T10:00:00Z");
        MatchEnrichmentState state = MatchEnrichmentState.builder()
                .provider(MatchProvider.SOFA_SCORE)
                .resource(MatchEnrichmentResource.PLAYER_STATISTICS)
                .build();

        state.markEmpty(now, now.plusSeconds(3600));
        assertEquals(MatchEnrichmentStatus.EMPTY, state.getStatus());
        assertEquals(now, state.getLastSuccessAt());
        assertEquals(now.plusSeconds(3600), state.getNextRetryAt());

        state.markSkipped(now.plusSeconds(1));
        assertEquals(MatchEnrichmentStatus.SKIPPED, state.getStatus());
        assertEquals(now, state.getLastSuccessAt(), "skipping a later attempt must not erase the previous successful fetch");
        assertEquals(now.plusSeconds(1), state.getNextRetryAt());
    }

    @Test
    void boundsProviderErrorMessageAndSchedulesRetry() {
        Instant retryAt = Instant.parse("2026-11-04T10:30:00Z");
        MatchEnrichmentState state = new MatchEnrichmentState();

        state.markFailed("HTTP_429", "x".repeat(1200), retryAt);

        assertEquals(MatchEnrichmentStatus.FAILED, state.getStatus());
        assertEquals("HTTP_429", state.getErrorCode());
        assertEquals(1000, state.getErrorMessage().length());
        assertEquals(retryAt, state.getNextRetryAt());
    }
}
