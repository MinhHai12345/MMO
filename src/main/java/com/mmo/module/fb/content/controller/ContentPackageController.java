package com.mmo.module.fb.content.controller;

import com.mmo.module.fb.content.service.ContentDraftService.DraftFormat;
import com.mmo.module.fb.content.production.ContentProductionPlanService;
import com.mmo.module.fb.content.production.ContentProductionPlanService.ProductionPlan;
import com.mmo.module.fb.content.service.SocialContentPackageService;
import com.mmo.module.fb.content.service.SocialContentPackageService.SocialContentPackage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/fb/content/workflows")
@RequiredArgsConstructor
public class ContentPackageController {

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
}