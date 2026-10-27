package com.mmo.module.fb.content.service.impl;

import com.mmo.module.fb.content.entity.ContentDraftRecordEntity;
import com.mmo.module.fb.content.repository.ContentDraftRecordRepository;
import com.mmo.module.fb.content.entity.ContentDraftVersionEntity;
import com.mmo.module.fb.content.repository.ContentDraftVersionRepository;
import com.mmo.module.fb.content.entity.ContentWorkflowRunEntity;
import com.mmo.module.fb.content.repository.ContentWorkflowRunRepository;
import com.mmo.module.fb.content.repository.ContentWorkflowItemRepository;
import com.mmo.module.fb.content.service.ContentLifecycleService;
import com.mmo.module.fb.content.service.ContentWorkflowOrchestrator;
import com.mmo.module.fb.content.service.ContentWorkflowOrchestrator.WorkflowCommand;
import com.mmo.module.fb.content.service.ContentWorkflowOrchestrator.WorkflowReport;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ContentLifecycleServiceImpl implements ContentLifecycleService {
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;
    private static final Set<String> REVIEW_STATUSES = Set.of("PENDING_REVIEW", "CHANGES_REQUESTED", "APPROVED", "REJECTED");
    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            "PENDING_REVIEW", Set.of("APPROVED", "CHANGES_REQUESTED", "REJECTED"),
            "CHANGES_REQUESTED", Set.of("PENDING_REVIEW", "APPROVED", "REJECTED"),
            "APPROVED", Set.of("CHANGES_REQUESTED"), "REJECTED", Set.of("CHANGES_REQUESTED"));

    private final ContentWorkflowOrchestrator orchestrator;
    private final ContentWorkflowPersistenceService persistenceService;
    private final ContentWorkflowRunRepository runRepository;
    private final ContentWorkflowItemRepository itemRepository;
    private final ContentDraftRecordRepository draftRepository;
    private final ContentDraftVersionRepository versionRepository;

    @Override
    public ContentWorkflowRunEntity generate(WorkflowCommand command) { return generate(command, null); }

    @Override
    public ContentWorkflowRunEntity generate(WorkflowCommand command, String idempotencyKey) {
        if (command == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workflow command is required");
        String key = normalizeIdempotencyKey(idempotencyKey);
        if (key != null) {
            ContentWorkflowRunEntity existing = runRepository.findByIdempotencyKey(key).orElse(null);
            if (existing != null) {
                if (!sameRequest(existing, command)) throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Idempotency-Key was already used with different workflow parameters");
                return reuseOrResume(existing);
            }
        }

        ContentWorkflowRunEntity run;
        try {
            run = persistenceService.reserve(command, key);
        } catch (DataIntegrityViolationException duplicateKeyRace) {
            if (key == null) throw duplicateKeyRace;
            ContentWorkflowRunEntity existing = runRepository.findByIdempotencyKey(key).orElseThrow(() -> duplicateKeyRace);
            if (!sameRequest(existing, command)) throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Idempotency-Key was already used with different workflow parameters");
            return reuseOrResume(existing);
        }

        String executionToken = run.getExecutionToken();
        try {
            WorkflowReport report = orchestrator.generate(command, Set.of(),
                    matchId -> persistenceService.startItem(run.getId(), executionToken, matchId),
                    item -> persistenceService.persistItem(run.getId(), executionToken, item));
            return persistenceService.persist(run, executionToken, command, report);
        } catch (RuntimeException failure) {
            persistenceService.markFailed(run.getId(), executionToken, failure);
            throw failure;
        }
    }

    @Override
    public ContentWorkflowRunEntity resume(Long runId) {
        if (runId == null || runId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A positive workflow run ID is required");
        }
        ContentWorkflowRunEntity run;
        try {
            run = persistenceService.claimForResume(runId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow run not found");
        }
        String executionToken = run.getExecutionToken();
        WorkflowCommand command = new WorkflowCommand(run.getFilterFrom(), run.getFilterTo(), run.getLeagueId(),
                run.getRequestedLimit(), run.getFormat());
        Set<Long> completedMatchIds = itemRepository.findByWorkflowRunIdOrderByIdAsc(runId).stream()
                .filter(item -> "COMPLETED".equals(item.getStatus()) || item.getAttemptCount() >= 3)
                .map(item -> item.getMatchId())
                .collect(java.util.stream.Collectors.toSet());
        try {
            WorkflowReport report = orchestrator.generate(command, completedMatchIds,
                    matchId -> persistenceService.startItem(runId, executionToken, matchId),
                    item -> persistenceService.persistItem(runId, executionToken, item));
            return persistenceService.persist(run, executionToken, command, report);
        } catch (RuntimeException failure) {
            persistenceService.markFailed(runId, executionToken, failure);
            throw failure;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<com.mmo.module.fb.content.entity.ContentWorkflowItemEntity> listWorkflowItems(Long runId) {
        if (runId == null || runId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A positive workflow run ID is required");
        }
        if (!runRepository.existsById(runId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow run not found");
        }
        return itemRepository.findByWorkflowRunIdOrderByIdAsc(runId);
    }

    /**
     * Reusing a scheduled idempotency key must not turn a transient failed run into a
     * permanent no-op. Completed runs are returned as-is; failed/partial or expired
     * RUNNING runs are resumed using their persisted per-match checkpoints.
     */
    private ContentWorkflowRunEntity reuseOrResume(ContentWorkflowRunEntity existing) {
        String status = existing.getStatus();
        if ("FAILED".equals(status) || "COMPLETED_WITH_ERRORS".equals(status) || "RUNNING".equals(status)) {
            return resume(existing.getId());
        }
        return existing;
    }

    private static String normalizeIdempotencyKey(String key) {
        if (key == null || key.isBlank()) return null;
        String normalized = key.strip();
        if (normalized.length() > MAX_IDEMPOTENCY_KEY_LENGTH) throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Idempotency-Key must not exceed 128 characters");
        return normalized;
    }

    private static boolean sameRequest(ContentWorkflowRunEntity existing, WorkflowCommand command) {
        int requestedLimit = command.limit() <= 0 ? 20 : Math.min(command.limit(), 50);
        var format = command.format() == null
                ? com.mmo.module.fb.content.service.ContentDraftService.DraftFormat.PRE_MATCH : command.format();
        return java.util.Objects.equals(existing.getFilterFrom(), command.from())
                && java.util.Objects.equals(existing.getFilterTo(), command.to())
                && java.util.Objects.equals(existing.getLeagueId(), command.leagueId())
                && existing.getRequestedLimit() == requestedLimit && existing.getFormat() == format;
    }

    @Override @Transactional(readOnly = true)
    public List<ContentWorkflowRunEntity> listRuns(int limit) {
        return runRepository.findAllByOrderByIdDesc(PageRequest.of(0, normalizeLimit(limit)));
    }
    @Override @Transactional(readOnly = true)
    public List<ContentDraftRecordEntity> listDrafts(String editorialStatus, int limit) {
        PageRequest page = PageRequest.of(0, normalizeLimit(limit));
        if (editorialStatus == null || editorialStatus.isBlank()) return draftRepository.findAllByOrderByIdDesc(page);
        String status = editorialStatus.strip().toUpperCase(Locale.ROOT);
        if (!REVIEW_STATUSES.contains(status)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported editorialStatus");
        return draftRepository.findByEditorialStatusOrderByIdDesc(status, page);
    }
    @Override @Transactional(readOnly = true)
    public ContentDraftRecordEntity getDraft(Long draftId) {
        if (draftId == null || draftId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A positive draftId is required");
        return draftRepository.findById(draftId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Draft not found"));
    }
    @Override @Transactional(readOnly = true)
    public List<ContentDraftVersionEntity> listVersions(Long draftId) {
        getDraft(draftId); return versionRepository.findByDraftIdOrderByVersionNumberDesc(draftId);
    }
    @Override @Transactional
    public ContentDraftRecordEntity review(Long draftId, ReviewCommand command) {
        if (command == null || command.status() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Review status is required");
        String requestedStatus = command.status().strip().toUpperCase(Locale.ROOT);
        if (!REVIEW_STATUSES.contains(requestedStatus) || "PENDING_REVIEW".equals(requestedStatus))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be APPROVED, CHANGES_REQUESTED, or REJECTED");
        ContentDraftRecordEntity draft = getDraft(draftId);
        String currentStatus = draft.getEditorialStatus() == null ? "PENDING_REVIEW" : draft.getEditorialStatus().toUpperCase(Locale.ROOT);
        if (!ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Set.of()).contains(requestedStatus))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Editorial status cannot transition from " + currentStatus + " to " + requestedStatus);
        if ("APPROVED".equals(requestedStatus) && "BLOCKED".equals(draft.getGenerationStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Blocked drafts cannot be approved; regenerate after fixing blockers");
        draft.setEditorialStatus(requestedStatus); draft.setReviewNotes(command.notes()); draft.setVersionNumber(draft.getVersionNumber() + 1);
        draft = draftRepository.save(draft);
        ContentDraftVersionEntity version = new ContentDraftVersionEntity();
        version.setDraft(draft); version.setVersionNumber(draft.getVersionNumber()); version.setChangeType("EDITORIAL_REVIEW");
        version.setEditorialStatus(draft.getEditorialStatus()); version.setChangedBy(command.changedBy()); version.setChangeNotes(command.notes());
        version.setSnapshotJson(persistenceService.snapshot(draft)); versionRepository.save(version);
        return draft;
    }
    private static int normalizeLimit(int limit) { return limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT); }
}
