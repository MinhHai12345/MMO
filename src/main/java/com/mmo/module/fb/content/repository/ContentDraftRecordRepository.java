package com.mmo.module.fb.content.repository;

import com.mmo.module.fb.content.entity.ContentDraftRecordEntity;
import com.mmo.module.fb.content.service.ContentDraftService.DraftFormat;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ContentDraftRecordRepository extends JpaRepository<ContentDraftRecordEntity, Long> {
    List<ContentDraftRecordEntity> findAllByOrderByIdDesc(Pageable pageable);
    List<ContentDraftRecordEntity> findByEditorialStatusOrderByIdDesc(String editorialStatus, Pageable pageable);
    List<ContentDraftRecordEntity> findByWorkflowRunIdOrderByIdAsc(Long workflowRunId);
    Optional<ContentDraftRecordEntity> findByWorkflowRunIdAndMatchId(Long workflowRunId, Long matchId);

    @Query("select distinct draft.matchId from ContentDraftRecordEntity draft where draft.format = :format")
    List<Long> findMatchIdsByFormat(@Param("format") DraftFormat format);
}
