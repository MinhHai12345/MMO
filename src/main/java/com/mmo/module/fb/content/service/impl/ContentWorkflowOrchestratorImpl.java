package com.mmo.module.fb.content.service.impl;

import com.mmo.module.fb.content.repository.ContentDraftRecordRepository;
import com.mmo.module.fb.content.service.ContentDraftService;
import com.mmo.module.fb.content.service.ContentDraftService.ContentDraft;
import com.mmo.module.fb.content.service.ContentDraftService.DraftFormat;
import com.mmo.module.fb.analytics.service.MatchSocialAnalyticsService;
import com.mmo.module.fb.analytics.service.MatchSocialAnalyticsService.ContentCandidate;
import com.mmo.module.fb.content.service.ContentWorkflowOrchestrator;
import com.mmo.module.fb.content.service.ContentWorkflowOrchestrator.WorkflowCommand;
import com.mmo.module.fb.content.service.ContentWorkflowOrchestrator.WorkflowItem;
import com.mmo.module.fb.content.service.ContentWorkflowOrchestrator.WorkflowReport;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class ContentWorkflowOrchestratorImpl implements ContentWorkflowOrchestrator {
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final MatchSocialAnalyticsService socialAnalyticsService;
    private final ContentDraftService contentDraftService;
    private final ContentDraftQualityGate qualityGate;
    private final ContentDraftRecordRepository draftRepository;

    @Override
    public WorkflowReport generate(WorkflowCommand command) {
        return generate(command, Set.of(), ignored -> {}, ignored -> {});
    }

    @Override
    public WorkflowReport generate(WorkflowCommand command, Set<Long> skipMatchIds,
                                   Consumer<Long> onStart, Consumer<WorkflowItem> onItem) {
        if (command == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workflow command is required");
        }
        if (command.from() != null && command.to() != null && command.from().isAfter(command.to())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'from' must not be after 'to'");
        }

        Instant startedAt = Instant.now();
        DraftFormat format = command.format() == null ? DraftFormat.PRE_MATCH : command.format();
        int limit = command.limit() <= 0 ? DEFAULT_LIMIT : Math.min(command.limit(), MAX_LIMIT);
        List<ContentCandidate> discovered = socialAnalyticsService.discover(
                command.from(), command.to(), command.leagueId(), limit);

        Map<Long, ContentCandidate> unique = new LinkedHashMap<>();
        for (ContentCandidate candidate : discovered) {
            if (candidate != null && candidate.matchId() != null) {
                unique.putIfAbsent(candidate.matchId(), candidate);
            }
        }
        List<ContentCandidate> candidates = List.copyOf(unique.values());
        List<WorkflowItem> items = new ArrayList<>(candidates.size());
        List<String> warnings = new ArrayList<>();

        Set<Long> skipped = new LinkedHashSet<>();
        if (skipMatchIds != null) {
            skipped.addAll(skipMatchIds);
        }
        // A later scheduled run must not create a second draft for the same match and format.
        skipped.addAll(draftRepository.findMatchIdsByFormat(format));

        int skippedCount = 0;
        for (ContentCandidate candidate : candidates) {
            if (skipped.contains(candidate.matchId())) {
                skippedCount++;
                continue;
            }
            if (onStart != null) {
                onStart.accept(candidate.matchId());
            }

            WorkflowItem item;
            try {
                ContentDraft draft = qualityGate.evaluate(contentDraftService.generate(candidate.matchId(), format));
                String status = switch (draft.status()) {
                    case "BLOCKED" -> "BLOCKED";
                    case "NEEDS_EDITOR_REVIEW" -> "NEEDS_EDITOR_REVIEW";
                    case "READY_FOR_EDITOR_REVIEW" -> "READY_FOR_EDITOR_REVIEW";
                    default -> "GENERATED";
                };
                item = new WorkflowItem(candidate.matchId(), candidate.kickoff(), candidate.competition(),
                        candidate.homeTeam(), candidate.awayTeam(), candidate.readinessScore(),
                        candidate.readinessLevel(), status, draft, null);
                if (draft.qualityReview() != null) {
                    warnings.addAll(draft.qualityReview().warnings());
                }
            } catch (RuntimeException ex) {
                String reason = ex instanceof ResponseStatusException rse && rse.getReason() != null
                        ? rse.getReason()
                        : "Draft generation failed (" + ex.getClass().getSimpleName() + ").";
                item = new WorkflowItem(candidate.matchId(), candidate.kickoff(), candidate.competition(),
                        candidate.homeTeam(), candidate.awayTeam(), candidate.readinessScore(),
                        candidate.readinessLevel(), "FAILED", null, reason);
                warnings.add("Match " + candidate.matchId() + ": " + reason);
            }

            items.add(item);
            if (onItem != null) {
                onItem.accept(item);
            }
        }

        int generated = (int) items.stream().filter(item -> item.draft() != null).count();
        int review = (int) items.stream().filter(item -> "NEEDS_EDITOR_REVIEW".equals(item.itemStatus())).count();
        int blocked = (int) items.stream().filter(item -> "BLOCKED".equals(item.itemStatus())).count();
        int failures = (int) items.stream().filter(item -> "FAILED".equals(item.itemStatus())).count();
        String status = candidates.isEmpty() ? "NO_CANDIDATES"
                : items.isEmpty() ? "NO_NEW_DRAFTS"
                : failures == items.size() ? "FAILED"
                : failures > 0 ? "COMPLETED_WITH_ERRORS"
                : blocked > 0 || review > 0 ? "COMPLETED_WITH_REVIEW" : "COMPLETED";

        if (discovered.size() != candidates.size()) {
            warnings.add("Duplicate match candidates were removed before draft generation.");
        }
        if (skippedCount > 0) {
            warnings.add(skippedCount + " candidate(s) were skipped because a draft of this format already exists or the item was checkpointed.");
        }
        if (candidates.isEmpty()) {
            warnings.add("No scheduled matches matched the discovery filters.");
        }
        warnings.add("Readiness score indicates data availability, not predicted match outcome or model reliability.");

        return new WorkflowReport(status, startedAt, Instant.now(), format, candidates.size(), generated,
                review, blocked, failures, items, warnings.stream().distinct().toList());
    }
}
