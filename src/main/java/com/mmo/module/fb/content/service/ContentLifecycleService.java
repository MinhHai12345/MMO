package com.mmo.module.fb.content.service;

import com.mmo.module.fb.content.persistence.ContentDraftRecordEntity;
import com.mmo.module.fb.content.persistence.ContentDraftVersionEntity;
import com.mmo.module.fb.content.persistence.ContentWorkflowRunEntity;
import com.mmo.module.fb.analytics.service.ContentDraftService.DraftFormat;
import com.mmo.module.fb.content.service.ContentWorkflowOrchestrator.WorkflowCommand;
import com.mmo.module.fb.content.service.ContentWorkflowOrchestrator.WorkflowReport;

import java.util.List;

public interface ContentLifecycleService {
    ContentWorkflowRunEntity generate(WorkflowCommand command);
    List<ContentWorkflowRunEntity> listRuns(int limit);
    List<ContentDraftRecordEntity> listDrafts(String editorialStatus, int limit);
    ContentDraftRecordEntity getDraft(Long draftId);
    List<ContentDraftVersionEntity> listVersions(Long draftId);
    ContentDraftRecordEntity review(Long draftId, ReviewCommand command);

    record ReviewCommand(String status, String notes, String changedBy) {}
}