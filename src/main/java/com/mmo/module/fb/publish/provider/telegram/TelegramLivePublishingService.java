package com.mmo.module.fb.publish.provider.telegram;

import com.mmo.module.fb.publish.domain.PublicationStateMachine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Coordinates durable claim -> remote send -> durable result.
 * The Telegram network request is deliberately outside a database transaction.
 */
@Service
@RequiredArgsConstructor
public class TelegramLivePublishingService {
    private final TelegramPublicationPersistenceService persistenceService;
    private final TelegramBotApiPublisher telegramPublisher;

    public LivePublishResponse publish(Long publicationId, String requestedBy) {
        TelegramPublicationPersistenceService.Claim claim = persistenceService.claim(publicationId, requestedBy);

        final TelegramBotApiPublisher.TelegramPublishResult sent;
        try {
            sent = telegramPublisher.publish(claim.title(), claim.body(), claim.mediaReference());
        } catch (TelegramBotApiPublisher.TelegramPublishingException ambiguousRemoteFailure) {
            persistenceService.markDeliveryUnknown(claim, ambiguousRemoteFailure);
            return new LivePublishResponse(claim.publicationId(), PublicationStateMachine.State.DELIVERY_UNKNOWN,
                    null, Instant.now(), "Telegram delivery outcome is unknown; reconcile channel history before retry");
        } catch (IllegalArgumentException | TelegramBotApiPublisher.TelegramConfigurationException localFailure) {
            persistenceService.markFailed(claim, localFailure);
            return new LivePublishResponse(claim.publicationId(), PublicationStateMachine.State.FAILED,
                    null, Instant.now(), "Telegram request was not sent: " + safeMessage(localFailure));
        } catch (RuntimeException unknownFailure) {
            // Conservative default: if the exception origin is not known, never risk an automatic duplicate.
            persistenceService.markDeliveryUnknown(claim, unknownFailure);
            return new LivePublishResponse(claim.publicationId(), PublicationStateMachine.State.DELIVERY_UNKNOWN,
                    null, Instant.now(), "Delivery outcome is unknown; reconcile channel history before retry");
        }

        persistenceService.markPublished(claim, sent);
        return new LivePublishResponse(claim.publicationId(), PublicationStateMachine.State.PUBLISHED,
                sent.messageId(), sent.completedAt(), "Published successfully through Telegram Bot API");
    }

    public void reconcileDeliveryUnknown(Long publicationId, boolean delivered, String externalPostId, String actor) {
        persistenceService.reconcileDeliveryUnknown(publicationId, delivered, externalPostId, actor);
    }

    private static String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) return failure.getClass().getSimpleName();
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    public record LivePublishResponse(Long publicationId, PublicationStateMachine.State state,
                                      String externalPostId, Instant completedAt, String message) {}
}
