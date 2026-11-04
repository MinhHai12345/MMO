package com.mmo.module.fb.match.repository;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.match.entity.MatchEnrichmentState;
import com.mmo.module.fb.match.enums.MatchEnrichmentResource;
import com.mmo.module.fb.match.enums.MatchEnrichmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MatchEnrichmentStateRepository extends JpaRepository<MatchEnrichmentState, Long> {

    Optional<MatchEnrichmentState> findByMatchIdAndProviderAndResource(
            Long matchId, Provider provider, MatchEnrichmentResource resource);

    List<MatchEnrichmentState> findByProviderAndResourceAndStatus(
            Provider provider, MatchEnrichmentResource resource, MatchEnrichmentStatus status);

    @Query("""
            SELECT state FROM MatchEnrichmentState state
            WHERE state.provider = :provider
              AND state.resource = :resource
              AND (state.nextRetryAt IS NULL OR state.nextRetryAt <= :now)
              AND (
                    state.status <> :inProgress
                    OR state.lastAttemptAt IS NULL
                    OR state.lastAttemptAt <= :staleBefore
              )
            ORDER BY state.nextRetryAt ASC, state.lastAttemptAt ASC, state.id ASC
            """)
    List<MatchEnrichmentState> findDue(
            @Param("provider") Provider provider,
            @Param("resource") MatchEnrichmentResource resource,
            @Param("now") Instant now,
            @Param("inProgress") MatchEnrichmentStatus inProgress,
            @Param("staleBefore") Instant staleBefore);
}
