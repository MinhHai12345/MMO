package com.mmo.module.fb.content.production;

import java.time.Instant;

/**
 * Provider adapter boundary for future platform integrations.
 * Implementations must use official platform APIs and platform-specific
 * permissions; this interface deliberately does not fake successful publishing.
 */
public interface ContentPublisher {
    String platform();

    PublishResult publish(PublishCommand command);

    record PublishCommand(String idempotencyKey, String title, String body,
                          String mediaReference, Instant scheduledAt) {
        public PublishCommand {
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                throw new IllegalArgumentException("idempotencyKey is required");
            }
            if (body == null || body.isBlank()) {
                throw new IllegalArgumentException("body is required");
            }
        }
    }

    record PublishResult(String platform, String externalPostId,
                         String status, String message, Instant completedAt) {}
}
