package com.mmo.module.fb.analytics.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmo.module.fb.analytics.persistence.*;
import com.mmo.module.fb.analytics.service.ContentDraftService.ContentDraft;
import com.mmo.module.fb.analytics.service.ContentDraftService.QualityReview;
import com.mmo.module.fb.analytics.service.ContentLifecycleService;
import com.mmo.module.fb.analytics.service.ContentWorkflowOrchestrator;
import com.mmo.module.fb.analytics.service.ContentWorkflowOrchestrator.WorkflowCommand;
import com.mmo.module.fb.analytics.service.ContentWorkflowOrchestrator.WorkflowItem;
import com.mmo.module.fb.analytics.service.ContentWorkflowOrchestrator.WorkflowReport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ContentLifecycleServiceImpl implements ContentLifecycleService {
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final Set<String> REVIEW_STATUSES = Set.of("PENDING_REVIEW", "CHANGES_REQUESTED", "APPROVED", "REJECTED");
    private final ContentWorkflowOrchestrator orchestrator;
    private final ContentWorkflowRunRepository runRepository;
    private final ContentDraftRecordRepository draftRepository;
    private final ContentDraftVersionRepository versionRepository;
    private final ObjectMapper objectMapper;

    @Override
    public ContentWorkflowRunEntity generate(WorkflowCommand command) {
        WorkflowReport report = orchestrator.generate(command);
        return persistReport(command, report);
    }

    @Transactional
    protected ContentWorkflowRunEntity persistReport(WorkflowCommand command, WorkflowReport report) {
        ContentWorkflowRunEntity run = new ContentWorkflowRunEntity();
        run.setStatus(report.status());
        run.setFormat(report.format());
        run.setStartedAt(report.startedAt());
        run.setCompletedAt(report.completedAt());
        run.setFilterFrom(command.from());
        run.setFilterTo(command.to());
        run.setLeagueId(command.leagueId());
        run.setRequestedLimit(command.limit() <= 0 ? DEFAULT_LIMIT : Math.min(command.limit(), 50));
        run.setCandidatesDiscovered(report.candidatesDiscovered());
        run.setDraftsGenerated(report.draftsGenerated());
        run.setDraftsNeedingReview(report.draftsNeedingReview());
        run.setDraftsBlocked(report.draftsBlocked());
        run.setFailures(report.failures());
        run.setWarningsJson(writeJson(report.warnings()));
        runRepository.save(run);

        for (WorkflowItem item : report.items()) {
            if (item.draft() == null) continue;
            ContentDraft draft = item.draft();
            QualityReview review = draft.qualityReview();
            ContentDraftRecordEntity record = new ContentDraftRecordEntity();
            record.setWorkflowRun(run);
            record.setMatchId(item.matchId());
            record.setFormat(draft.format());
            record.setGenerationStatus(item.itemStatus());
            record.setEditorialStatus("PENDING_REVIEW");
            record.setQualityDecision(review == null ? null : review.decision());
            record.setQualityScore(review == null ? null : review.score());
            record.setTitle(draft.title());
            record.setOpeningHook(draft.openingHook());
            record.setOutlineJson(writeJson(draft.outline()));
            record.setShortHooksJson(writeJson(draft.shortHooks()));
            record.setEvidenceJson(writeJson(draft.evidence()));
            record.setCaveatsJson(writeJson(draft.caveats()));
            record.setBlockersJson(writeJson(review == null ? List.of() : review.blockers()));
            record.setWarningsJson(writeJson(review == null ? List.of() : review.warnings()));
            record.setVersionNumber(1);
            record.setCreatedAtSnapshot(Instant.now());
            record = draftRepository.save(record);
            saveVersion(record, "GENERATED", null, "System", null);
        }
        return run;
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
        saveVersion(draft, "EDITORIAL_REVIEW", command.changedBy(), command.notes());
        return draft;
    }

    private void saveVersion(ContentDraftRecordEntity draft, String changeType, String changedBy, String notes) {
        ContentDraftVersionEntity version = new ContentDraftVersionEntity();
        version.setDraft(draft);
        version.setVersionNumber(draft.getVersionNumber());
        version.setChangeType(changeType);
        version.setEditorialStatus(draft.getEditorialStatus());
        version.setChangedBy(changedBy);
        version.setChangeNotes(notes);
        version.setSnapshotJson(writeJson(new DraftSnapshot(draft.getId(), draft.getMatchId(), draft.getFormat().name(),
                draft.getGenerationStatus(), draft.getEditorialStatus(), draft.getTitle(), draft.getOpeningHook(),
                draft.getOutlineJson(), draft.getShortHooksJson(), draft.getEvidenceJson(), draft.getCaveatsJson(),
                draft.getBlockersJson(), draft.getWarningsJson(), draft.getQualityDecision(), draft.getQualityScore(),
                draft.getReviewNotes(), draft.getVersionNumber())));
        versionRepository.save(version);
    }

    private String writeJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Could not serialize content workflow data", ex); }
    }

    private static int normalizeLimit(int limit) { return limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT); }

    private record DraftSnapshot(Long id, Long matchId, String format, String generationStatus, String editorialStatus,
                                 String title, String openingHook, String outlineJson, String shortHooksJson,
                                 String evidenceJson, String caveatsJson, String blockersJson, String warningsJson,
                                 String qualityDecision, Integer qualityScore, String reviewNotes, int version) {}
}