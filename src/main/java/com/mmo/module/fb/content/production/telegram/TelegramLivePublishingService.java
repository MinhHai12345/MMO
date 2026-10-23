package com.mmo.module.fb.content.production.telegram;

import com.mmo.module.fb.content.production.PublicationPlatform;
import com.mmo.module.fb.content.production.PublicationStateMachine;
import com.mmo.module.fb.content.production.persistence.ContentPublicationAttemptEntity;
import com.mmo.module.fb.content.production.persistence.ContentPublicationAttemptRepository;
import com.mmo.module.fb.content.production.persistence.ContentPublicationEntity;
import com.mmo.module.fb.content.production.persistence.ContentPublicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TelegramLivePublishingService {
    private final ContentPublicationRepository publicationRepository;
    private final ContentPublicationAttemptRepository attemptRepository;
    private final TelegramBotApiPublisher telegramPublisher;

    @Transactional
    public LivePublishResponse publish(Long publicationId, String requestedBy) {
        if (publicationId == null || publicationId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A positive publicationId is required");
        }
        if (requestedBy == null || requestedBy.isBlank() || requestedBy.strip().length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Publishing-Actor is required (max 120 characters)");
        }
        ContentPublicationEntity publication = publicationRepository.findByIdForUpdate(publicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Publication not found"));
        if (publication.getPlatform() != PublicationPlatform.TELEGRAM) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This endpoint only publishes TELEGRAM publications");
        }
        if (publication.getApprovedAt() == null || publication.getApprovedBy() == null
                || publication.getApprovedBy().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Publication must be explicitly approved first");
        }
        if (publication.getScheduledAt() != null && publication.getScheduledAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Publication is scheduled for a future time");
        }
        PublicationStateMachine.State state = publication.getState();
        if (state != PublicationStateMachine.State.APPROVED
                && state != PublicationStateMachine.State.SCHEDULED
                && state != PublicationStateMachine.State.FAILED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only APPROVED, due SCHEDULED, or FAILED Telegram publications can be sent");
        }

        int attemptNumber = publication.getAttemptCount() + 1;
        Instant startedAt = Instant.now();
        ContentPublicationAttemptEntity attempt = new ContentPublicationAttemptEntity();
        attempt.setPublication(publication);
        attempt.setAttemptNumber(attemptNumber);
        attempt.setExecutionMode("LIVE_TELEGRAM");
        attempt.setStatus("RUNNING");
        attempt.setRequestedBy(requestedBy.strip());
        attempt.setStartedAt(startedAt);
        attempt.setRequestSummary("Telegram Bot API; mediaAttached=" + (publication.getMediaReference() != null));
        attemptRepository.saveAndFlush(attempt);

        publication.setState(PublicationStateMachine.transition(state, PublicationStateMachine.State.PUBLISHING));
        publication.setAttemptCount(attemptNumber);
        publication.setLastAttemptAt(startedAt);
        publication.setLastAttemptStatus("RUNNING");
        publication.setLastAttemptMessage("Sending through the official Telegram Bot API");
        publication.setUpdatedAtSnapshot(startedAt);
        publicationRepository.saveAndFlush(publication);

        try {
            TelegramBotApiPublisher.TelegramPublishResult sent = telegramPublisher.publish(
                    publication.getTitle(), publication.getBody(), publication.getMediaReference());
            Instant completedAt = sent.completedAt() == null ? Instant.now() : sent.completedAt();
            publication.setState(PublicationStateMachine.transition(
                    PublicationStateMachine.State.PUBLISHING, PublicationStateMachine.State.PUBLISHED));
            publication.setExternalPostId(sent.messageId());
            publication.setPublishedAt(completedAt);
            publication.setLastAttemptStatus("PUBLISHED");
            publication.setLastAttemptMessage("Telegram message_id=" + sent.messageId());
            publication.setLastAttemptAt(completedAt);
            publication.setUpdatedAtSnapshot(completedAt);
            attempt.setStatus("PUBLISHED");
            attempt.setResponseSummary("Telegram message_id=" + sent.messageId() + ", chat_id=" + sent.chatId());
            attempt.setCompletedAt(completedAt);
            publicationRepository.save(publication);
            attemptRepository.save(attempt);
            return new LivePublishResponse(publication.getId(), publication.getState(),
                    sent.messageId(), completedAt, "Published successfully through Telegram Bot API");
        } catch (RuntimeException failure) {
            Instant completedAt = Instant.now();
            publication.setState(PublicationStateMachine.transition(
                    PublicationStateMachine.State.PUBLISHING, PublicationStateMachine.State.FAILED));
            String safeMessage = safeMessage(failure);
            publication.setLastAttemptStatus("FAILED");
            publication.setLastAttemptMessage(safeMessage);
            publication.setLastAttemptAt(completedAt);
            publication.setUpdatedAtSnapshot(completedAt);
            attempt.setStatus("FAILED");
            attempt.setResponseSummary(safeMessage);
            attempt.setCompletedAt(completedAt);
            publicationRepository.save(publication);
            attemptRepository.save(attempt);
            return new LivePublishResponse(publication.getId(), publication.getState(),
                    null, completedAt, "Telegram publishing failed: " + safeMessage);
        }
    }

    private static String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) return failure.getClass().getSimpleName();
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    public record LivePublishResponse(Long publicationId, PublicationStateMachine.State state,
                                      String externalPostId, Instant completedAt, String message) {}
}
