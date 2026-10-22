package com.mmo.module.fb.analytics.service.impl;

import com.mmo.module.fb.analytics.service.ContentDraftService;
import com.mmo.module.fb.analytics.service.ContentDraftService.ContentDraft;
import com.mmo.module.fb.analytics.service.ContentDraftService.DraftFormat;
import com.mmo.module.fb.analytics.service.ContentWorkflowOrchestrator;
import com.mmo.module.fb.analytics.service.ContentWorkflowOrchestrator.WorkflowCommand;
import com.mmo.module.fb.analytics.service.ContentWorkflowOrchestrator.WorkflowItem;
import com.mmo.module.fb.analytics.service.ContentWorkflowOrchestrator.WorkflowReport;
import com.mmo.module.fb.analytics.service.MatchSocialAnalyticsService;
import com.mmo.module.fb.analytics.service.MatchSocialAnalyticsService.ContentCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentWorkflowOrchestratorImpl implements ContentWorkflowOrchestrator {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final MatchSocialAnalyticsService socialAnalyticsService;
    private final ContentDraftService contentDraftService;

    @Override
    public WorkflowReport generate(WorkflowCommand command) {
        if (command == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workflow command is required");
        }

        Instant startedAt = Instant.now();
        DraftFormat format = command.format() == null ? DraftFormat.PRE_MATCH : command.format();
        int limit = command.limit() <= 0 ? DEFAULT_LIMIT : Math.min(command.limit(), MAX_LIMIT);

        List<ContentCandidate> candidates = socialAnalyticsService.discover(
                command.from(), command.to(), command.leagueId(), limit);
        List<WorkflowItem> items = new ArrayList<>(candidates.size());
        List<String> warnings = new ArrayList<>();

        for (ContentCandidate candidate : candidates) {
            try {
                ContentDraft draft = contentDraftService.generate(candidate.matchId(), format);
                String status = switch (draft.status()) {
                    case "BLOCKED" -> "BLOCKED";
                    case "NEEDS_EDITOR_REVIEW" -> "NEEDS_EDITOR_REVIEW";
                    case "READY_FOR_EDITOR_REVIEW" -> "READY_FOR_EDITOR_REVIEW";
                    default -> "GENERATED";
                };
                items.add(new WorkflowItem(candidate.matchId(), candidate.kickoff(), candidate.competition(),
                        candidate.homeTeam(), candidate.awayTeam(), candidate.readinessScore(),
                        candidate.readinessLevel(), status, draft, null));
                if (draft.qualityReview() != null) {
                    warnings.addAll(draft.qualityReview().warnings());
                }
            } catch (RuntimeException ex) {
                String reason = safeFailureReason(ex);
                items.add(new WorkflowItem(candidate.matchId(), candidate.kickoff(), candidate.competition(),
                        candidate.homeTeam(), candidate.awayTeam(), candidate.readinessScore(),
                        candidate.readinessLevel(), "FAILED", null, reason));
                warnings.add("Match " + candidate.matchId() + ": " + reason);
            }
        }

        int generated = (int) items.stream().filter(item -> item.draft() != null).count();
        int needsReview = (int) items.stream().filter(item -> "NEEDS_EDITOR_REVIEW".equals(item.itemStatus())).count();
        int blocked = (int) items.stream().filter(item -> "BLOCKED".equals(item.itemStatus())).count();
        int failures = (int) items.stream().filter(item -> "FAILED".equals(item.itemStatus())).count();
        String status = candidates.isEmpty() ? "NO_CANDIDATES"
                : failures == candidates.size() ? "FAILED"
                : failures > 0 ? "COMPLETED_WITH_ERRORS"
                : blocked > 0 || needsReview > 0 ? "COMPLETED_WITH_REVIEW"
                : "COMPLETED";

        if (candidates.isEmpty()) {
            warnings.add("No scheduled matches matched the discovery filters.");
        }
        warnings.add("Workflow output is not persisted; generated drafts are returned in this report only.");
        warnings.add("Readiness score indicates data availability, not predicted match outcome or model reliability.");

        return new WorkflowReport(status, startedAt, Instant.now(), format, candidates.size(), generated,
                needsReview, blocked, failures, items, warnings.stream().distinct().toList());
    }

    private static String safeFailureReason(RuntimeException ex) {
        if (ex instanceof ResponseStatusException responseStatusException
                && responseStatusException.getReason() != null
                && !responseStatusException.getReason().isBlank()) {
            return responseStatusException.getReason();
        }
        return "Draft generation failed (" + ex.getClass().getSimpleName() + ").";
    }
}
