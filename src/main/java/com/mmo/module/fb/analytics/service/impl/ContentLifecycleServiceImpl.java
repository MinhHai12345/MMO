package com.mmo.module.fb.analytics.service.impl;

import com.mmo.module.fb.analytics.persistence.ContentDraftRecordEntity;
import com.mmo.module.fb.analytics.persistence.ContentDraftRecordRepository;
import com.mmo.module.fb.analytics.persistence.ContentDraftVersionEntity;
import com.mmo.module.fb.analytics.persistence.ContentDraftVersionRepository;
import com.mmo.module.fb.analytics.persistence.ContentWorkflowRunEntity;
import com.mmo.module.fb.analytics.persistence.ContentWorkflowRunRepository;
import com.mmo.module.fb.analytics.service.ContentLifecycleService;
import com.mmo.module.fb.analytics.service.ContentWorkflowOrchestrator;
import com.mmo.module.fb.analytics.service.ContentWorkflowOrchestrator.WorkflowCommand;
import com.mmo.module.fb.analytics.service.ContentWorkflowOrchestrator.WorkflowReport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ContentLifecycleServiceImpl implements ContentLifecycleService {
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final Set<String> REVIEW_STATUSES = Set.of("PENDING_REVIEW", "CHANGES_REQUESTED", "APPROVED", "REJECTED");

    private final ContentWorkflowOrchestrator orchestrator;
    private final ContentWorkflowPersistenceService persistenceService;
    private final ContentWorkflowRunRepository runRepository;
    private final ContentDraftRecordRepository draftRepository;
    private final ContentDraftVersionRepository versionRepository;

    @Override
    public ContentWorkflowRunEntity generate(WorkflowCommand command) {
        WorkflowReport report = orchestrator.generate(command);
        return persistenceService.persist(command, report);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContentWorkflowRunEntity> listRuns(int limit) {
        return runRepository.findAllByOrderByIdDesc(PageRequest.of(0, normalizeLimit(limit)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContentDraftRecordEntity> listDrafts(String editorialStatus, int limit) {
        PageRequest page = PageRequest.of(0, normalizeLimit(limit));
        if (editorialStatus == null || editorialStatus.isBlank()) return draftRepository.findAllByOrderByIdDesc(page);
        if (!REVIEW_STATUSES.contains(editorialStatus)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported editorialStatus");
        return draftRepository.findByEditorialStatusOrderByIdDesc(editorialStatus, page);
    }

    @Override
    @Transactional(readOnly = true)
    public ContentDraftRecordEntity getDraft(Long draftId) {
        return draftRepository.findById(draftId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Draft not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContentDraftVersionEntity> listVersions(Long draftId) {
        getDraft(draftId);
        return versionRepository.findByDraftIdOrderByVersionNumberDesc(draftId);
    }

    @Override
    @Transactional
    public ContentDraftRecordEntity review(Long draftId, ReviewCommand command) {
        if (command == null || command.status() == null || !REVIEW_STATUSES.contains(command.status()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be PENDING_REVIEW, CHANGES_REQUESTED, APPROVED, or REJECTED");
        ContentDraftRecordEntity draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Draft not found"));
        if ("APPROVED".equals(command.status()) && "BLOCKED".equals(draft.getGenerationStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Blocked drafts cannot be approved; regenerate after fixing blockers");
        draft.setEditorialStatus(command.status());
        draft.setReviewNotes(command.notes());
        draft.setVersionNumber(draft.getVersionNumber() + 1);
        draft = draftRepository.save(draft);
        ContentDraftVersionEntity version = new ContentDraftVersionEntity();
        version.setDraft(draft);
        version.setVersionNumber(draft.getVersionNumber());
        version.setChangeType("EDITORIAL_REVIEW");
        version.setEditorialStatus(draft.getEditorialStatus());
        version.setChangedBy(command.changedBy());
        version.setChangeNotes(command.notes());
        version.setSnapshotJson(persistenceService.snapshot(draft));
        versionRepository.save(version);
        return draft;
    }

    private static int normalizeLimit(int limit) { return limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT); }
}