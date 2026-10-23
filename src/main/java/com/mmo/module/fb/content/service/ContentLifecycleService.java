package com.mmo.module.fb.content.service;

import com.mmo.module.fb.content.service.ContentDraftService.DraftFormat;
import com.mmo.module.fb.content.entity.ContentDraftRecordEntity;
import com.mmo.module.fb.content.entity.ContentDraftVersionEntity;
import com.mmo.module.fb.content.entity.ContentWorkflowRunEntity;
import com.mmo.module.fb.content.entity.ContentWorkflowItemEntity;
import com.mmo.module.fb.content.service.ContentWorkflowOrchestrator.WorkflowCommand;

import java.util.List;

public interface ContentLifecycleService {
    ContentWorkflowRunEntity generate(WorkflowCommand command);
    ContentWorkflowRunEntity generate(WorkflowCommand command, String idempotencyKey);
    ContentWorkflowRunEntity resume(Long runId);
    List<ContentWorkflowItemEntity> listWorkflowItems(Long runId);
    List<ContentWorkflowRunEntity> listRuns(int limit);
    List<ContentDraftRecordEntity> listDrafts(String editorialStatus, int limit);
    ContentDraftRecordEntity getDraft(Long draftId);
    List<ContentDraftVersionEntity> listVersions(Long draftId);
    ContentDraftRecordEntity review(Long draftId, ReviewCommand command);

    record ReviewCommand(String status, String notes, String changedBy) {}
}
