package com.mmo.module.fb.publish.service.impl;

import com.mmo.module.fb.publish.domain.PublicationPlatform;
import com.mmo.module.fb.publish.domain.PublicationStateMachine;
import com.mmo.module.fb.publish.provider.telegram.TelegramLivePublishingService;
import com.mmo.module.fb.publish.repository.ContentPublicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Application service used by the Quartz publication worker job.
 * Only approved, due Telegram publications are eligible for live publishing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentPublicationWorker {

    private static final List<PublicationStateMachine.State> ACTIVE_STATES = List.of(
            PublicationStateMachine.State.APPROVED,
            PublicationStateMachine.State.SCHEDULED);

    private final ContentPublicationRepository publicationRepository;
    private final TelegramLivePublishingService telegramLivePublishingService;

    public void processDuePublications() {
        Instant now = Instant.now();
        int configuredBatchSize = 10;
        int configuredMaxAttempts = 5;
        long configuredRetryDelayMillis = 300_000L;

        List<Long> dueIds = publicationRepository.findDuePublicationIds(
                PublicationPlatform.TELEGRAM,
                ACTIVE_STATES,
                PublicationStateMachine.State.FAILED,
                now,
                now.minus(configuredRetryDelayMillis, ChronoUnit.MILLIS),
                configuredMaxAttempts,
                PageRequest.of(0, configuredBatchSize));

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
                log.warn("Skipped publicationId={} because it is no longer publishable: {}",
                        publicationId, conflictOrInvalidState.getReason());
            } catch (RuntimeException failure) {
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
