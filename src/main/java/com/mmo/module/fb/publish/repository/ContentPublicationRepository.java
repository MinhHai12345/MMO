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

import java.util.List;
import java.util.Optional;

public interface ContentPublicationRepository extends JpaRepository<ContentPublicationEntity, Long> {

    Optional<ContentPublicationEntity> findByPlatformAndIdempotencyKey(
            PublicationPlatform platform, String idempotencyKey);

    List<ContentPublicationEntity> findAllByOrderByCreatedAtSnapshotDesc(Pageable pageable);

    List<ContentPublicationEntity> findByStateOrderByCreatedAtSnapshotDesc(
            PublicationStateMachine.State state, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select publication from ContentPublicationEntity publication where publication.id = :id")
    Optional<ContentPublicationEntity> findByIdForUpdate(@Param("id") Long id);
}
