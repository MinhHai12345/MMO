package com.mmo.module.fb.content.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContentWorkflowItemRepository extends JpaRepository<ContentWorkflowItemEntity, Long> {
    List<ContentWorkflowItemEntity> findByWorkflowRunIdOrderByIdAsc(Long workflowRunId);
    Optional<ContentWorkflowItemEntity> findByWorkflowRunIdAndMatchId(Long workflowRunId, Long matchId);
    long countByWorkflowRunIdAndStatus(Long workflowRunId, String status);
}
