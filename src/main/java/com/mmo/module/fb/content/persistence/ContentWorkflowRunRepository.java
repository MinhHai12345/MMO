package com.mmo.module.fb.content.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContentWorkflowRunRepository extends JpaRepository<ContentWorkflowRunEntity, Long> {
    List<ContentWorkflowRunEntity> findAllByOrderByIdDesc(Pageable pageable);
    Optional<ContentWorkflowRunEntity> findByIdempotencyKey(String idempotencyKey);
}
