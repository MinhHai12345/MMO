package com.mmo.module.fb.publish.provider.telegram;

import com.mmo.AppProperties;
import com.mmo.module.fb.publish.domain.PublicationPlatform;
import com.mmo.module.fb.publish.domain.PublicationStateMachine;
import com.mmo.module.fb.publish.entity.ContentPublicationAttemptEntity;
import com.mmo.module.fb.publish.entity.ContentPublicationEntity;
import com.mmo.module.fb.publish.repository.ContentPublicationAttemptRepository;
import com.mmo.module.fb.publish.repository.ContentPublicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Persists a delivery claim before any external Telegram API request is made. */
@Service
@RequiredArgsConstructor
public class TelegramPublicationPersistenceService {
    private static final Duration STALE_DELIVERY_THRESHOLD = Duration.ofMinutes(10);
    private final ContentPublicationRepository publicationRepository;
    private final ContentPublicationAttemptRepository attemptRepository;
    private final AppProperties appProperties;

    @Transactional
    public Claim claim(Long publicationId, String requestedBy) {
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
        Instant now = Instant.now();
        if (publication.getScheduledAt() != null && publication.getScheduledAt().isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Publication is scheduled for a future time");
        }
        PublicationStateMachine.State state = publication.getState();
        int maxAttempts = Math.max(1, appProperties.getPublication().getWorker().getMaxAttempts());
        if (publication.getAttemptCount() >= maxAttempts) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Publication reached the configured maximum attempt count: " + maxAttempts);
        }
        if (state != PublicationStateMachine.State.APPROVED
                && state != PublicationStateMachine.State.SCHEDULED
                && state != PublicationStateMachine.State.FAILED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only APPROVED, due SCHEDULED, or retryable FAILED publications can be sent");
        }

        int attemptNumber = publication.getAttemptCount() + 1;
        ContentPublicationAttemptEntity attempt = new ContentPublicationAttemptEntity();
        attempt.setPublication(publication);
        attempt.setAttemptNumber(attemptNumber);
        attempt.setExecutionMode("LIVE_TELEGRAM");
        attempt.setStatus("RUNNING");
        attempt.setRequestedBy(requestedBy.strip());
        attempt.setStartedAt(now);
        attempt.setRequestSummary("Telegram Bot API; mediaAttached=" + (publication.getMediaReference() != null));
        attemptRepository.saveAndFlush(attempt);

        publication.setState(PublicationStateMachine.transition(state, PublicationStateMachine.State.PUBLISHING));
        publication.setAttemptCount(attemptNumber);
        publication.setLastAttemptAt(now);
        publication.setLastAttemptStatus("RUNNING");
        publication.setLastAttemptMessage("Delivery claim persisted before Telegram API request");
        publication.setUpdatedAtSnapshot(now);
        publicationRepository.saveAndFlush(publication);
        return new Claim(publication.getId(), attempt.getId(), publication.getTitle(),
                publication.getBody(), publication.getMediaReference());
    }

    @Transactional
    public void markPublished(Claim claim, TelegramBotApiPublisher.TelegramPublishResult sent) {
        ContentPublicationEntity publication = lockedPublishing(claim.publicationId());
        Instant now = sent.completedAt() == null ? Instant.now() : sent.completedAt();
        publication.setState(PublicationStateMachine.transition(
                PublicationStateMachine.State.PUBLISHING, PublicationStateMachine.State.PUBLISHED));
        publication.setExternalPostId(sent.messageId());
        publication.setPublishedAt(now);
        publication.setLastAttemptStatus("PUBLISHED");
        publication.setLastAttemptMessage("Telegram message_id=" + sent.messageId());
        publication.setLastAttemptAt(now);
        publication.setUpdatedAtSnapshot(now);
        publicationRepository.save(publication);
        ContentPublicationAttemptEntity attempt = attempt(claim);
        attempt.setStatus("PUBLISHED");
        attempt.setResponseSummary("Telegram message_id=" + sent.messageId() + ", chat_id=" + sent.chatId());
        attempt.setCompletedAt(now);
        attemptRepository.save(attempt);
    }

    /** Local validation/configuration errors are safe to retry after correction. */
    @Transactional
    public void markFailed(Claim claim, RuntimeException failure) {
        finishAttempt(claim, PublicationStateMachine.State.FAILED, "FAILED", safeMessage(failure));
    }

    /** A remote exception can occur after Telegram accepted the request; do not blindly resend. */
    @Transactional
    public void markDeliveryUnknown(Claim claim, RuntimeException failure) {
        finishAttempt(claim, PublicationStateMachine.State.DELIVERY_UNKNOWN,
                "DELIVERY_UNKNOWN", safeMessage(failure));
    }

    @Transactional
    public void reconcileDeliveryUnknown(Long publicationId, boolean delivered, String externalPostId, String actor) {
        if (actor == null || actor.isBlank() || actor.strip().length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A reconciliation actor is required");
        }
        ContentPublicationEntity publication = publicationRepository.findByIdForUpdate(publicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Publication not found"));
        if (publication.getState() != PublicationStateMachine.State.DELIVERY_UNKNOWN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only DELIVERY_UNKNOWN publications can be reconciled");
        }
        Instant now = Instant.now();
        if (delivered) {
            if (externalPostId == null || externalPostId.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "externalPostId is required when delivery is confirmed");
            }
            publication.setState(PublicationStateMachine.transition(
                    PublicationStateMachine.State.DELIVERY_UNKNOWN, PublicationStateMachine.State.PUBLISHED));
            publication.setExternalPostId(externalPostId.strip());
            publication.setPublishedAt(now);
            publication.setLastAttemptStatus("PUBLISHED_RECONCILED");
            publication.setLastAttemptMessage("Delivery confirmed manually by " + actor.strip());
        } else {
            publication.setState(PublicationStateMachine.transition(
                    PublicationStateMachine.State.DELIVERY_UNKNOWN, PublicationStateMachine.State.FAILED));
            publication.setLastAttemptStatus("NOT_DELIVERED_RECONCILED");
            publication.setLastAttemptMessage("Channel history checked; delivery not found. Reconciled by " + actor.strip());
        }
        publication.setLastAttemptAt(now);
        publication.setUpdatedAtSnapshot(now);
        publicationRepository.save(publication);
        attemptRepository.findByPublicationIdAndAttemptNumber(publication.getId(), publication.getAttemptCount())
                .ifPresent(attempt -> {
                    attempt.setStatus(delivered ? "PUBLISHED_RECONCILED" : "NOT_DELIVERED_RECONCILED");
                    attempt.setResponseSummary(publication.getLastAttemptMessage());
                    attempt.setCompletedAt(now);
                    attemptRepository.save(attempt);
                });
    }

    @Transactional
    public int quarantineStaleDeliveries() {
        Instant cutoff = Instant.now().minus(STALE_DELIVERY_THRESHOLD);
        List<ContentPublicationEntity> stale = publicationRepository.findByStateAndLastAttemptAtBefore(
                PublicationStateMachine.State.PUBLISHING, cutoff);
        List<ContentPublicationEntity> changed = new ArrayList<>();
        Instant now = Instant.now();
        for (ContentPublicationEntity publication : stale) {
            publication.setState(PublicationStateMachine.transition(
                    PublicationStateMachine.State.PUBLISHING, PublicationStateMachine.State.DELIVERY_UNKNOWN));
            publication.setLastAttemptStatus("DELIVERY_UNKNOWN");
            publication.setLastAttemptMessage("Stale PUBLISHING state; reconcile remote delivery before retry");
            publication.setUpdatedAtSnapshot(now);
            changed.add(publication);
            attemptRepository.findByPublicationIdAndAttemptNumber(publication.getId(), publication.getAttemptCount())
                    .ifPresent(attempt -> {
                        attempt.setStatus("DELIVERY_UNKNOWN");
                        attempt.setResponseSummary("Worker lease expired; outcome is ambiguous; do not resend automatically");
                        attempt.setCompletedAt(now);
                        attemptRepository.save(attempt);
                    });
        }
        publicationRepository.saveAll(changed);
        return changed.size();
    }

    private void finishAttempt(Claim claim, PublicationStateMachine.State target, String status, String message) {
        ContentPublicationEntity publication = lockedPublishing(claim.publicationId());
        Instant now = Instant.now();
        publication.setState(PublicationStateMachine.transition(PublicationStateMachine.State.PUBLISHING, target));
        publication.setLastAttemptStatus(status);
        publication.setLastAttemptMessage(message);
        publication.setLastAttemptAt(now);
        publication.setUpdatedAtSnapshot(now);
        publicationRepository.save(publication);
        ContentPublicationAttemptEntity attempt = attempt(claim);
        attempt.setStatus(status);
        attempt.setResponseSummary(message);
        attempt.setCompletedAt(now);
        attemptRepository.save(attempt);
    }

    private ContentPublicationAttemptEntity attempt(Claim claim) {
        return attemptRepository.findById(claim.attemptId())
                .orElseThrow(() -> new IllegalStateException("Publication attempt not found: " + claim.attemptId()));
    }

    private ContentPublicationEntity lockedPublishing(Long id) {
        ContentPublicationEntity publication = publicationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalStateException("Publication not found: " + id));
        if (publication.getState() != PublicationStateMachine.State.PUBLISHING) {
            throw new IllegalStateException("Publication is not PUBLISHING: " + id + ", state=" + publication.getState());
        }
        return publication;
    }

    private static String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) return failure.getClass().getSimpleName();
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    public record Claim(Long publicationId, Long attemptId, String title, String body, String mediaReference) {}
}
