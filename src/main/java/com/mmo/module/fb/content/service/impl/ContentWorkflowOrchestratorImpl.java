package com.mmo.module.fb.content.service.impl;

import com.mmo.module.fb.analytics.service.ContentDraftService;
import com.mmo.module.fb.analytics.service.ContentDraftService.ContentDraft;
import com.mmo.module.fb.analytics.service.ContentDraftService.DraftFormat;
import com.mmo.module.fb.content.service.ContentWorkflowOrchestrator;
import com.mmo.module.fb.content.service.ContentWorkflowOrchestrator.WorkflowCommand;
import com.mmo.module.fb.content.service.ContentWorkflowOrchestrator.WorkflowItem;
import com.mmo.module.fb.content.service.ContentWorkflowOrchestrator.WorkflowReport;
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
    private final ContentDraftQualityGate qualityGate;

    @Override
    public WorkflowReport generate(WorkflowCommand command) {
        if (command == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workflow command is required");
        if (command.from() != null && command.to() != null && command.from().isAfter(command.to()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'from' must not be after 'to'");
        Instant startedAt = Instant.now();
        DraftFormat format = command.format() == null ? DraftFormat.PRE_MATCH : command.format();
        int limit = command.limit() <= 0 ? DEFAULT_LIMIT : Math.min(command.limit(), MAX_LIMIT);
        List<ContentCandidate> candidates = socialAnalyticsService.discover(command.from(), command.to(), command.leagueId(), limit);
        List<WorkflowItem> items = new ArrayList<>(candidates.size());
        List<String> warnings = new ArrayList<>();
        for (ContentCandidate candidate : candidates) {
            try {
                ContentDraft draft = qualityGate.evaluate(contentDraftService.generate(candidate.matchId(), format));
                String status = switch (draft.status()) {
                    case "BLOCKED" -> "BLOCKED";
                    case "NEEDS_EDITOR_REVIEW" -> "NEEDS_EDITOR_REVIEW";
                    case "READY_FOR_EDITOR_REVIEW" -> "READY_FOR_EDITOR_REVIEW";
                    default -> "GENERATED";
                };
                items.add(new WorkflowItem(candidate.matchId(), candidate.kickoff(), candidate.competition(),
                        candidate.homeTeam(), candidate.awayTeam(), candidate.readinessScore(), candidate.readinessLevel(),
                        status, draft, null));
                if (draft.qualityReview() != null) warnings.addAll(draft.qualityReview().warnings());
            } catch (RuntimeException ex) {
                String reason = ex instanceof ResponseStatusException rse && rse.getReason() != null
                        ? rse.getReason() : "Draft generation failed (" + ex.getClass().getSimpleName() + ").";
                items.add(new WorkflowItem(candidate.matchId(), candidate.kickoff(), candidate.competition(),
                        candidate.homeTeam(), candidate.awayTeam(), candidate.readinessScore(), candidate.readinessLevel(),
                        "FAILED", null, reason));
                warnings.add("Match " + candidate.matchId() + ": " + reason);
            }
        }
        int generated = (int) items.stream().filter(i -> i.draft() != null).count();
        int review = (int) items.stream().filter(i -> "NEEDS_EDITOR_REVIEW".equals(i.itemStatus())).count();
        int blocked = (int) items.stream().filter(i -> "BLOCKED".equals(i.itemStatus())).count();
        int failures = (int) items.stream().filter(i -> "FAILED".equals(i.itemStatus())).count();
        String status = candidates.isEmpty() ? "NO_CANDIDATES" : failures == candidates.size() ? "FAILED"
                : failures > 0 ? "COMPLETED_WITH_ERRORS" : blocked > 0 || review > 0 ? "COMPLETED_WITH_REVIEW" : "COMPLETED";
        if (candidates.isEmpty()) warnings.add("No scheduled matches matched the discovery filters.");
        warnings.add("Readiness score indicates data availability, not predicted match outcome or model reliability.");
        return new WorkflowReport(status, startedAt, Instant.now(), format, candidates.size(), generated, review,
                blocked, failures, items, warnings.stream().distinct().toList());
    }
}