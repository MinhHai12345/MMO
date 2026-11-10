package com.mmo.module.fb.workflow.infrastructure.persistence.repository;

import com.mmo.module.fb.workflow.domain.entity.FootballWorkflowRunEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface FootballWorkflowRunRepository extends JpaRepository<FootballWorkflowRunEntity, Long> {

    Optional<FootballWorkflowRunEntity> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from FootballWorkflowRunEntity r where r.idempotencyKey = :key")
    Optional<FootballWorkflowRunEntity> findByIdempotencyKeyForUpdate(@Param("key") String key);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from FootballWorkflowRunEntity r where r.id = :id")
    Optional<FootballWorkflowRunEntity> findByIdForUpdate(@Param("id") Long id);
}
