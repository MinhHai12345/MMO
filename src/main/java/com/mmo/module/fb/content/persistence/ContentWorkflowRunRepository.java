package com.mmo.module.fb.content.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ContentWorkflowRunRepository extends JpaRepository<ContentWorkflowRunEntity, Long> {
    List<ContentWorkflowRunEntity> findAllByOrderByIdDesc(Pageable pageable);
    Optional<ContentWorkflowRunEntity> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ContentWorkflowRunEntity r where r.id = :id")
    Optional<ContentWorkflowRunEntity> findByIdForUpdate(@Param("id") Long id);
}
