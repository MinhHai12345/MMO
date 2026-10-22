package com.mmo.module.fb.content.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmo.module.fb.content.persistence.ContentDraftRecordEntity;
import com.mmo.module.fb.content.persistence.ContentDraftRecordRepository;
import com.mmo.module.fb.content.persistence.ContentDraftVersionEntity;
import com.mmo.module.fb.content.persistence.ContentDraftVersionRepository;
import com.mmo.module.fb.content.persistence.ContentWorkflowRunEntity;
import com.mmo.module.fb.content.persistence.ContentWorkflowRunRepository;
import com.mmo.module.fb.content.persistence.ContentWorkflowItemEntity;
import com.mmo.module.fb.content.persistence.ContentWorkflowItemRepository;
import com.mmo.module.fb.analytics.service.ContentDraftService.ContentDraft;
import com.mmo.module.fb.analytics.service.ContentDraftService.QualityReview;
import com.mmo.module.fb.content.service.ContentWorkflowOrchestrator.WorkflowCommand;
import com.mmo.module.fb.content.service.ContentWorkflowOrchestrator.WorkflowItem;
import com.mmo.module.fb.content.service.ContentWorkflowOrchestrator.WorkflowReport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ContentWorkflowPersistenceService {
    private final ContentWorkflowRunRepository runRepository;
    private final ContentWorkflowItemRepository itemRepository;
    private final ContentDraftRecordRepository draftRepository;
    private final ContentDraftVersionRepository versionRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public ContentWorkflowRunEntity reserve(WorkflowCommand command, String idempotencyKey) {
        ContentWorkflowRunEntity run = new ContentWorkflowRunEntity();
        run.setIdempotencyKey(idempotencyKey);
        run.setStatus("RUNNING");
        run.setFormat(command.format() == null ? com.mmo.module.fb.analytics.service.ContentDraftService.DraftFormat.PRE_MATCH : command.format());
        run.setStartedAt(Instant.now());
        run.setFilterFrom(command.from());
        run.setFilterTo(command.to());
        run.setLeagueId(command.leagueId());
        run.setRequestedLimit(normalizeLimit(command.limit()));
        run.setCandidatesDiscovered(0);
        run.setDraftsGenerated(0);
        run.setDraftsNeedingReview(0);
        run.setDraftsBlocked(0);
        run.setFailures(0);
        run.setWarningsJson("[]");
        return runRepository.saveAndFlush(run);
    }

    @Transactional
    public ContentWorkflowRunEntity persist(ContentWorkflowRunEntity run, WorkflowCommand command, WorkflowReport report) {
        run.setStatus(report.status());
        run.setFormat(report.format());
        run.setStartedAt(report.startedAt());
        run.setCompletedAt(report.completedAt());
        run.setFilterFrom(command.from());
        run.setFilterTo(command.to());
        run.setLeagueId(command.leagueId());
        run.setRequestedLimit(normalizeLimit(command.limit()));
        List<ContentWorkflowItemEntity> checkpoints = itemRepository.findByWorkflowRunIdOrderByIdAsc(run.getId());
        List<ContentDraftRecordEntity> drafts = draftRepository.findByWorkflowRunIdOrderByIdAsc(run.getId());
        long failures = checkpoints.stream().filter(item -> "FAILED".equals(item.getStatus())).count();
        long blocked = drafts.stream().filter(draft -> "BLOCKED".equals(draft.getGenerationStatus())).count();
        long review = drafts.stream().filter(draft -> "NEEDS_EDITOR_REVIEW".equals(draft.getGenerationStatus())).count();
        run.setCandidatesDiscovered(checkpoints.size());
        run.setDraftsGenerated(drafts.size());
        run.setDraftsNeedingReview((int) review);
        run.setDraftsBlocked((int) blocked);
        run.setFailures((int) failures);
        String finalStatus = checkpoints.isEmpty() ? "NO_CANDIDATES"
                : failures == checkpoints.size() ? "FAILED"
                : failures > 0 ? "COMPLETED_WITH_ERRORS"
                : blocked > 0 || review > 0 ? "COMPLETED_WITH_REVIEW" : "COMPLETED";
        run.setStatus(finalStatus);
        run.setWarningsJson(writeJson(report.warnings()));
        runRepository.save(run);

        return run;
    }

    @Transactional
    public ContentWorkflowRunEntity claimForResume(Long runId) {
        ContentWorkflowRunEntity run = runRepository.findByIdForUpdate(runId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow run not found: " + runId));
        boolean staleRunning = "RUNNING".equals(run.getStatus())
                && run.getStartedAt() != null
                && run.getStartedAt().isBefore(Instant.now().minus(Duration.ofMinutes(10)));
        if ("RUNNING".equals(run.getStatus()) && !staleRunning) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "Workflow is still running");
        }
        if (!staleRunning && !Set.of("FAILED", "COMPLETED_WITH_ERRORS").contains(run.getStatus())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "Only failed or partially failed workflows can be resumed");
        }
        itemRepository.findByWorkflowRunIdOrderByIdAsc(runId).stream()
                .filter(item -> !"COMPLETED".equals(item.getStatus()) && item.getAttemptCount() >= 3)
                .forEach(item -> {
                    item.setStatus("FAILED");
                    item.setFailureReason("Retry limit of 3 attempts reached.");
                    item.setCompletedAt(Instant.now());
                    itemRepository.save(item);
                });
        run.setStatus("RUNNING");
        run.setStartedAt(Instant.now());
        run.setCompletedAt(null);
        return runRepository.save(run);
    }

    @Transactional
    public void startItem(Long runId, Long matchId) {
        ContentWorkflowRunEntity run = runRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow run not found: " + runId));
        ContentWorkflowItemEntity item = itemRepository.findByWorkflowRunIdAndMatchId(runId, matchId)
                .orElseGet(ContentWorkflowItemEntity::new);
        item.setWorkflowRun(run);
        item.setMatchId(matchId);
        item.setStatus("RUNNING");
        item.setAttemptCount(item.getAttemptCount() + 1);
        item.setStartedAt(Instant.now());
        item.setCompletedAt(null);
        item.setFailureReason(null);
        itemRepository.save(item);
    }

    @Transactional
    public void persistItem(Long runId, WorkflowItem item) {
        ContentWorkflowRunEntity run = runRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow run not found: " + runId));
        ContentWorkflowItemEntity checkpoint = itemRepository.findByWorkflowRunIdAndMatchId(runId, item.matchId())
                .orElseGet(ContentWorkflowItemEntity::new);
        checkpoint.setWorkflowRun(run);
        checkpoint.setMatchId(item.matchId());
        checkpoint.setCompletedAt(Instant.now());
        checkpoint.setFailureReason(item.failureReason());
        if (item.draft() == null) {
            checkpoint.setStatus("FAILED");
            itemRepository.save(checkpoint);
            return;
        }
        ContentDraft draft = item.draft();
        ContentDraftRecordEntity record = draftRepository.findByWorkflowRunIdAndMatchId(runId, item.matchId()).orElse(null);
        if (record == null) {
            QualityReview review = draft.qualityReview();
            record = new ContentDraftRecordEntity();
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
            ContentDraftVersionEntity version = new ContentDraftVersionEntity();
            version.setDraft(record);
            version.setVersionNumber(1);
            version.setChangeType("GENERATED");
            version.setEditorialStatus(record.getEditorialStatus());
            version.setChangedBy("System");
            version.setSnapshotJson(snapshot(record));
            versionRepository.save(version);
        }
        checkpoint.setStatus("COMPLETED");
        checkpoint.setDraftId(record.getId());
        itemRepository.save(checkpoint);
    }

    @Transactional
    public void markFailed(Long runId, RuntimeException failure) {
        runRepository.findById(runId).ifPresent(run -> {
            run.setStatus("FAILED");
            run.setCompletedAt(Instant.now());
            String reason = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            run.setWarningsJson(writeJson(List.of("Workflow execution failed: " + reason)));
            runRepository.save(run);
        });
    }

    private static int normalizeLimit(int limit) {
        return limit <= 0 ? 20 : Math.min(limit, 50);
    }

    public String snapshot(ContentDraftRecordEntity draft) {
        return writeJson(new DraftSnapshot(draft.getId(), draft.getMatchId(), draft.getFormat().name(),
                draft.getGenerationStatus(), draft.getEditorialStatus(), draft.getTitle(), draft.getOpeningHook(),
                draft.getOutlineJson(), draft.getShortHooksJson(), draft.getEvidenceJson(), draft.getCaveatsJson(),
                draft.getBlockersJson(), draft.getWarningsJson(), draft.getQualityDecision(), draft.getQualityScore(),
                draft.getReviewNotes(), draft.getVersionNumber()));
    }

    private String writeJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Could not serialize content workflow data", ex); }
    }

    private record DraftSnapshot(Long id, Long matchId, String format, String generationStatus, String editorialStatus,
                                 String title, String openingHook, String outlineJson, String shortHooksJson,
                                 String evidenceJson, String caveatsJson, String blockersJson, String warningsJson,
                                 String qualityDecision, Integer qualityScore, String reviewNotes, int version) {}
}
