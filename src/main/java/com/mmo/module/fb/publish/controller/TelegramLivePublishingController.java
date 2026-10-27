package com.mmo.module.fb.publish.controller;

import com.mmo.module.fb.publish.provider.telegram.TelegramLivePublishingService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping({"/api/fb/publish/publications", "/api/fb/content/publications"})
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
        authorize(suppliedKey);
        return livePublishingService.publish(publicationId, actor);
    }

    /**
     * Reconcile a DELIVERY_UNKNOWN publication only after checking the target Telegram channel.
     * This is manual because Telegram's sendMessage API does not provide an idempotency key.
     */
    @PostMapping("/{publicationId}/reconcile/telegram")
    public void reconcile(
            @PathVariable Long publicationId,
            @RequestHeader(value = "X-Publishing-Key", required = false) String suppliedKey,
            @RequestBody ReconciliationCommand command) {
        authorize(suppliedKey);
        if (command == null || command.delivered() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "delivered is required");
        }
        livePublishingService.reconcileDeliveryUnknown(publicationId, command.delivered(),
                command.externalPostId(), command.actor());
    }

    public record ReconciliationCommand(Boolean delivered, String externalPostId, String actor) {}

    private void authorize(String suppliedKey) {
        if (publishingApiKey == null || publishingApiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Live publishing is disabled until CONTENT_PUBLISHING_API_KEY is configured");
        }
        if (suppliedKey == null || !MessageDigest.isEqual(
                publishingApiKey.getBytes(StandardCharsets.UTF_8),
                suppliedKey.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid publishing credentials");
        }
    }
}
