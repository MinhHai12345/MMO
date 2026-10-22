package com.mmo.module.fb.content.controller;

import com.mmo.module.fb.analytics.service.ContentDraftService.DraftFormat;
import com.mmo.module.fb.content.persistence.ContentDraftRecordEntity;
import com.mmo.module.fb.content.persistence.ContentDraftVersionEntity;
import com.mmo.module.fb.content.persistence.ContentWorkflowRunEntity;
import com.mmo.module.fb.content.production.ContentProductionPlanService;
import com.mmo.module.fb.content.production.ContentProductionPlanService.ProductionPlan;
import com.mmo.module.fb.content.service.ContentLifecycleService;
import com.mmo.module.fb.content.service.ContentLifecycleService.ReviewCommand;
import com.mmo.module.fb.content.service.ContentWorkflowOrchestrator.WorkflowCommand;
import com.mmo.module.fb.content.service.SocialContentPackageService;
import com.mmo.module.fb.content.service.SocialContentPackageService.SocialContentPackage;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/fb/content/workflows")
@RequiredArgsConstructor
public class ContentWorkflowController {

    private final ContentLifecycleService lifecycleService;
    private final SocialContentPackageService socialContentPackageService;
    private final ContentProductionPlanService contentProductionPlanService;

    @GetMapping("/matches/{matchId}/social-package")
    public SocialContentPackage socialPackage(
            @PathVariable Long matchId,
            @RequestParam(defaultValue = "PRE_MATCH") DraftFormat format
    ) {
        return socialContentPackageService.generate(matchId, format);
    }

    @GetMapping("/matches/{matchId}/production-plan")
    public ProductionPlan productionPlan(
            @PathVariable Long matchId,
            @RequestParam(defaultValue = "PRE_MATCH") DraftFormat format
    ) {
        return contentProductionPlanService.create(matchId, format);
    }

    @PostMapping("/generate")
    public ContentWorkflowRunEntity generate(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Long leagueId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "PRE_MATCH") DraftFormat format
    ) {
        return lifecycleService.generate(new WorkflowCommand(from, to, leagueId, limit, format));
    }

    @GetMapping("/runs")
    public List<ContentWorkflowRunEntity> listRuns(
            @RequestParam(defaultValue = "20") int limit
    ) {
        return lifecycleService.listRuns(limit);
    }

    @GetMapping("/drafts")
    public List<ContentDraftRecordEntity> listDrafts(
            @RequestParam(required = false) String editorialStatus,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return lifecycleService.listDrafts(editorialStatus, limit);
    }

    @GetMapping("/drafts/{draftId}")
    public ContentDraftRecordEntity getDraft(@PathVariable Long draftId) {
        return lifecycleService.getDraft(draftId);
    }

    @GetMapping("/drafts/{draftId}/versions")
    public List<ContentDraftVersionEntity> listVersions(@PathVariable Long draftId) {
        return lifecycleService.listVersions(draftId);
    }

    @PatchMapping("/drafts/{draftId}/review")
    public ContentDraftRecordEntity review(
            @PathVariable Long draftId,
            @RequestBody ReviewCommand command
    ) {
        return lifecycleService.review(draftId, command);
    }
}