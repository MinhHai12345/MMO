package com.mmo.module.fb.content.controller;

import com.mmo.module.fb.content.service.ContentDraftService;
import com.mmo.module.fb.content.service.ContentDraftService.ContentDraft;
import com.mmo.module.fb.content.service.ContentDraftService.DraftFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Content-owned endpoint for editorial draft generation.
 * The legacy route is retained for API compatibility.
 */
@RestController
@RequestMapping("/api/fb/analytics/matches")
@RequiredArgsConstructor
public class ContentDraftController {

    private final ContentDraftService contentDraftService;

    @GetMapping("/{matchId}/content-draft")
    public ContentDraft getContentDraft(
            @PathVariable Long matchId,
            @RequestParam(defaultValue = "PRE_MATCH") DraftFormat format) {
        return contentDraftService.generate(matchId, format);
    }
}
