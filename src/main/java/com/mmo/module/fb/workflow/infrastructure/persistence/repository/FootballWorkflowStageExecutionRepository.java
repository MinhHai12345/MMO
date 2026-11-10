package com.mmo.module.fb.workflow.infrastructure.persistence.repository;

import com.mmo.module.fb.workflow.domain.entity.FootballWorkflowStageExecutionEntity;
import com.mmo.module.fb.workflow.domain.enums.FootballWorkflowStage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FootballWorkflowStageExecutionRepository
        extends JpaRepository<FootballWorkflowStageExecutionEntity, Long> {

    Optional<FootballWorkflowStageExecutionEntity> findByWorkflowRunIdAndStage(
            Long workflowRunId, FootballWorkflowStage stage);

    List<FootballWorkflowStageExecutionEntity> findAllByWorkflowRunIdOrderByIdAsc(Long workflowRunId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from FootballWorkflowStageExecutionEntity s
            where s.workflowRun.id = :runId and s.stage = :stage
            """)
    Optional<FootballWorkflowStageExecutionEntity> findForUpdate(
            @Param("runId") Long runId, @Param("stage") FootballWorkflowStage stage);
}
