package com.mmo.module.fb.content.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmo.module.fb.content.persistence.ContentDraftRecordEntity;
import com.mmo.module.fb.content.persistence.ContentDraftRecordRepository;
import com.mmo.module.fb.content.persistence.ContentDraftVersionEntity;
import com.mmo.module.fb.content.persistence.ContentDraftVersionRepository;
import com.mmo.module.fb.content.persistence.ContentWorkflowRunEntity;
import com.mmo.module.fb.content.persistence.ContentWorkflowRunRepository;
import com.mmo.module.fb.analytics.service.ContentDraftService.ContentDraft;
import com.mmo.module.fb.analytics.service.ContentDraftService.QualityReview;
import com.mmo.module.fb.content.service.ContentWorkflowOrchestrator.WorkflowCommand;
import com.mmo.module.fb.content.service.ContentWorkflowOrchestrator.WorkflowItem;
import com.mmo.module.fb.content.service.ContentWorkflowOrchestrator.WorkflowReport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContentWorkflowPersistenceService {
    private final ContentWorkflowRunRepository runRepository;
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
            ContentDraftVersionEntity version = new ContentDraftVersionEntity();
            version.setDraft(record);
            version.setVersionNumber(1);
            version.setChangeType("GENERATED");
            version.setEditorialStatus(record.getEditorialStatus());
            version.setChangedBy("System");
            version.setSnapshotJson(snapshot(record));
            versionRepository.save(version);
        }
        return run;
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
