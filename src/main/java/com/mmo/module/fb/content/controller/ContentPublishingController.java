package com.mmo.module.fb.content.controller;

import com.mmo.module.fb.content.production.ContentPublishingService;
import com.mmo.module.fb.content.production.ContentPublishingService.ApprovalCommand;
import com.mmo.module.fb.content.production.ContentPublishingService.CreatePublicationCommand;
import com.mmo.module.fb.content.production.ContentPublishingService.DryRunCommand;
import com.mmo.module.fb.content.production.ContentPublishingService.PublicationDetails;
import com.mmo.module.fb.content.production.ContentPublishingService.PublicationSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fb/content/publications")
@RequiredArgsConstructor
public class ContentPublishingController {

    private final ContentPublishingService publishingService;

    @PostMapping
    public PublicationDetails create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody CreatePublicationCommand command) {
        return publishingService.create(idempotencyKey, command);
    }

    @GetMapping
    public List<PublicationSummary> list(
            @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "20") int limit) {
        return publishingService.list(state, limit);
    }

    @GetMapping("/{publicationId}")
    public PublicationDetails get(@PathVariable Long publicationId) {
        return publishingService.get(publicationId);
    }

    @PostMapping("/{publicationId}/approve")
    public PublicationDetails approve(
            @PathVariable Long publicationId,
            @RequestBody ApprovalCommand command) {
        return publishingService.approve(publicationId, command);
    }

    /**
     * Validates the stored payload and records an audit attempt.
     * This endpoint never sends content to a social platform.
     */
    @PostMapping("/{publicationId}/dry-run")
    public PublicationDetails dryRun(
            @PathVariable Long publicationId,
            @RequestBody DryRunCommand command) {
        return publishingService.dryRun(publicationId, command);
    }
}
