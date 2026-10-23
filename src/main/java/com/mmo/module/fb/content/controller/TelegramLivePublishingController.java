package com.mmo.module.fb.content.controller;

import com.mmo.module.fb.content.production.telegram.TelegramLivePublishingService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/fb/content/publications")
@RequiredArgsConstructor
public class TelegramLivePublishingController {
    private final TelegramLivePublishingService livePublishingService;

    @Value("${app.content.publishing.api-key:}")
    private String publishingApiKey;

    @PostMapping("/{publicationId}/publish/telegram")
    public TelegramLivePublishingService.LivePublishResponse publish(
            @PathVariable Long publicationId,
            @RequestHeader(value = "X-Publishing-Key", required = false) String suppliedKey,
            @RequestHeader(value = "X-Publishing-Actor", required = false) String actor) {
        if (publishingApiKey == null || publishingApiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Live publishing is disabled until CONTENT_PUBLISHING_API_KEY is configured");
        }
        if (suppliedKey == null || !MessageDigest.isEqual(
                publishingApiKey.getBytes(StandardCharsets.UTF_8),
                suppliedKey.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid publishing credentials");
        }
        return livePublishingService.publish(publicationId, actor);
    }
}
