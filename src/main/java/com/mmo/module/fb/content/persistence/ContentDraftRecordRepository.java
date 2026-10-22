package com.mmo.module.fb.content.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContentDraftRecordRepository extends JpaRepository<ContentDraftRecordEntity, Long> {
    List<ContentDraftRecordEntity> findAllByOrderByIdDesc(Pageable pageable);
    List<ContentDraftRecordEntity> findByEditorialStatusOrderByIdDesc(String editorialStatus, Pageable pageable);
    List<ContentDraftRecordEntity> findByWorkflowRunIdOrderByIdAsc(Long workflowRunId);
    Optional<ContentDraftRecordEntity> findByWorkflowRunIdAndMatchId(Long workflowRunId, Long matchId);
}