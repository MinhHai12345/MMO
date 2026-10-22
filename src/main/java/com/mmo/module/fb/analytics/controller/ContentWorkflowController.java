package com.mmo.module.fb.analytics.controller;

import com.mmo.module.fb.analytics.service.ContentDraftService.DraftFormat;
import com.mmo.module.fb.analytics.service.ContentWorkflowOrchestrator;
import com.mmo.module.fb.analytics.service.ContentWorkflowOrchestrator.WorkflowCommand;
import com.mmo.module.fb.analytics.service.ContentWorkflowOrchestrator.WorkflowReport;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/fb/analytics/content-workflows")
@RequiredArgsConstructor
public class ContentWorkflowController {

    private final ContentWorkflowOrchestrator orchestrator;

    @PostMapping("/generate")
    public WorkflowReport generate(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Long leagueId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "PRE_MATCH") DraftFormat format) {
        return orchestrator.generate(new WorkflowCommand(from, to, leagueId, limit, format));
    }
}
