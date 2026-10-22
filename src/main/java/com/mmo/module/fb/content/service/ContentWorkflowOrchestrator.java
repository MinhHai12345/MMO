package com.mmo.module.fb.content.service;

import com.mmo.module.fb.analytics.service.ContentDraftService.ContentDraft;
import com.mmo.module.fb.analytics.service.ContentDraftService.DraftFormat;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public interface ContentWorkflowOrchestrator {
    WorkflowReport generate(WorkflowCommand command);
    WorkflowReport generate(WorkflowCommand command, Set<Long> skipMatchIds, Consumer<Long> onStart, Consumer<WorkflowItem> onItem);

    record WorkflowCommand(Instant from, Instant to, Long leagueId, int limit, DraftFormat format) {}

    record WorkflowReport(String status, Instant startedAt, Instant completedAt, DraftFormat format,
                          int candidatesDiscovered, int draftsGenerated, int draftsNeedingReview,
                          int draftsBlocked, int failures, List<WorkflowItem> items, List<String> warnings) {
        public WorkflowReport {
            items = items == null ? List.of() : List.copyOf(items);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }

    record WorkflowItem(Long matchId, Instant kickoff, String competition, String homeTeam, String awayTeam,
                        int readinessScore, String readinessLevel, String itemStatus,
                        ContentDraft draft, String failureReason) {}
}