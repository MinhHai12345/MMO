package com.mmo.module.fb.publish.service.impl;

import com.mmo.module.fb.publish.domain.PublicationPlatform;
import com.mmo.module.fb.publish.domain.PublicationStateMachine;
import com.mmo.module.fb.publish.provider.telegram.TelegramLivePublishingService;
import com.mmo.module.fb.publish.repository.ContentPublicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Polls the canonical publication table and sends only approved, due Telegram publications.
 *
 * <p>The worker intentionally does not claim support for platforms without a live adapter.
 * The publishing service locks and re-checks each row before making the external API call,
 * so overlapping scheduler ticks cannot publish the same persisted row concurrently.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.content.publishing.worker", name = "enabled",
        havingValue = "true", matchIfMissing = false)
public class ContentPublicationWorker {

    private static final List<PublicationStateMachine.State> ACTIVE_STATES = List.of(
            PublicationStateMachine.State.APPROVED,
            PublicationStateMachine.State.SCHEDULED);

    private final ContentPublicationRepository publicationRepository;
    private final TelegramLivePublishingService telegramLivePublishingService;

    @org.springframework.beans.factory.annotation.Value("${app.content.publishing.worker.batch-size:10}")
    private int configuredBatchSize;

    @org.springframework.beans.factory.annotation.Value("${app.content.publishing.worker.max-attempts:5}")
    private int configuredMaxAttempts;

    @org.springframework.beans.factory.annotation.Value("${app.content.publishing.worker.retry-delay-ms:300000}")
    private long configuredRetryDelayMillis;

    @Scheduled(
            fixedDelayString = "${app.content.publishing.worker.fixed-delay-ms:30000}",
            initialDelayString = "${app.content.publishing.worker.initial-delay-ms:15000}")
    public void processDuePublications() {
        Instant now = Instant.now();
        int batchSize = Math.max(1, Math.min(configuredBatchSize, 100));
        int maxAttempts = Math.max(1, Math.min(configuredMaxAttempts, 10));
        long retryDelayMillis = Math.max(1_000L, configuredRetryDelayMillis);

        List<Long> dueIds = publicationRepository.findDuePublicationIds(
                PublicationPlatform.TELEGRAM,
                ACTIVE_STATES,
                PublicationStateMachine.State.FAILED,
                now,
                now.minus(retryDelayMillis, ChronoUnit.MILLIS),
                maxAttempts,
                PageRequest.of(0, batchSize));

        if (dueIds.isEmpty()) {
            return;
        }

        log.info("Publication worker found {} due Telegram publication(s)", dueIds.size());
        for (Long publicationId : dueIds) {
            try {
                TelegramLivePublishingService.LivePublishResponse result =
                        telegramLivePublishingService.publish(publicationId, "fb-publication-worker");
                if (result.state() == PublicationStateMachine.State.PUBLISHED) {
                    log.info("Published Telegram publicationId={} externalPostId={}",
                            publicationId, result.externalPostId());
                } else {
                    log.warn("Telegram publicationId={} finished in state {}: {}",
                            publicationId, result.state(), result.message());
                }
            } catch (ResponseStatusException conflictOrInvalidState) {
                // A different worker may have completed the row after this batch was queried.
                log.warn("Skipped publicationId={} because it is no longer publishable: {}",
                        publicationId, conflictOrInvalidState.getReason());
            } catch (RuntimeException failure) {
                // One broken publication must not prevent the rest of this batch from running.
                log.error("Unexpected worker failure for publicationId={}: {}",
                        publicationId, safeMessage(failure), failure);
            }
        }
    }

    private static String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return failure.getClass().getSimpleName();
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
