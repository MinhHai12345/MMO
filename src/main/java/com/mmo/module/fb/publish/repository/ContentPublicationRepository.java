package com.mmo.module.fb.publish.repository;

import com.mmo.module.fb.publish.entity.ContentPublicationEntity;
import com.mmo.module.fb.publish.domain.PublicationPlatform;
import com.mmo.module.fb.publish.domain.PublicationStateMachine;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ContentPublicationRepository extends JpaRepository<ContentPublicationEntity, Long> {

    Optional<ContentPublicationEntity> findByPlatformAndIdempotencyKey(
            PublicationPlatform platform, String idempotencyKey);

    Optional<ContentPublicationEntity> findByDraftIdAndPlatform(Long draftId, PublicationPlatform platform);

    List<ContentPublicationEntity> findAllByOrderByCreatedAtSnapshotDesc(Pageable pageable);

    List<ContentPublicationEntity> findByStateOrderByCreatedAtSnapshotDesc(
            PublicationStateMachine.State state, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select publication from ContentPublicationEntity publication where publication.id = :id")
    Optional<ContentPublicationEntity> findByIdForUpdate(@Param("id") Long id);

    /**
     * Returns only due Telegram publications that have passed editorial and publication approval.
     * Failed publications are eligible again only after the configured retry delay.
     */
    @Query("""
            select publication.id
            from ContentPublicationEntity publication
            where publication.platform = :platform
              and publication.approvedAt is not null
              and publication.attemptCount < :maxAttempts
              and (publication.scheduledAt is null or publication.scheduledAt <= :now)
              and (
                    publication.state in :activeStates
                    or (publication.state = :failedState
                        and (publication.lastAttemptAt is null or publication.lastAttemptAt <= :retryBefore))
              )
            order by coalesce(publication.scheduledAt, publication.createdAtSnapshot) asc
            """)
    List<Long> findDuePublicationIds(
            @Param("platform") PublicationPlatform platform,
            @Param("activeStates") List<PublicationStateMachine.State> activeStates,
            @Param("failedState") PublicationStateMachine.State failedState,
            @Param("now") Instant now,
            @Param("retryBefore") Instant retryBefore,
            @Param("maxAttempts") int maxAttempts,
            Pageable pageable);

    List<ContentPublicationEntity> findByStateAndLastAttemptAtBefore(
            PublicationStateMachine.State state, Instant cutoff, Pageable pageable);
}
