package com.mmo.module.fb.publish.provider.telegram;

import com.mmo.module.fb.publish.domain.PublicationPlatform;
import com.mmo.module.fb.publish.domain.PublicationStateMachine;
import com.mmo.module.fb.publish.entity.ContentPublicationAttemptEntity;
import com.mmo.module.fb.publish.repository.ContentPublicationAttemptRepository;
import com.mmo.module.fb.publish.entity.ContentPublicationEntity;
import com.mmo.module.fb.publish.repository.ContentPublicationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TelegramLivePublishingServiceTest {
    private final ContentPublicationRepository publicationRepository = mock(ContentPublicationRepository.class);
    private final ContentPublicationAttemptRepository attemptRepository = mock(ContentPublicationAttemptRepository.class);
    private final TelegramBotApiPublisher telegramPublisher = mock(TelegramBotApiPublisher.class);
    private final TelegramLivePublishingService service =
            new TelegramLivePublishingService(publicationRepository, attemptRepository, telegramPublisher);

    @Test
    void publishesApprovedTelegramPublicationAndStoresMessageId() {
        ContentPublicationEntity publication = approvedPublication();
        when(publicationRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(publication));
        when(telegramPublisher.publish("Preview", "Home vs Away", null))
                .thenReturn(new TelegramBotApiPublisher.TelegramPublishResult("1234", "-10012", Instant.now()));

        var response = service.publish(7L, "operator");

        assertEquals(PublicationStateMachine.State.PUBLISHED, response.state());
        assertEquals("1234", response.externalPostId());
        assertEquals("1234", publication.getExternalPostId());
        assertEquals("PUBLISHED", publication.getLastAttemptStatus());
        verify(attemptRepository).saveAndFlush(any(ContentPublicationAttemptEntity.class));
        verify(attemptRepository).save(argThat(attempt -> "PUBLISHED".equals(attempt.getStatus())));
    }

    @Test
    void recordsFailedAttemptWhenTelegramApiFails() {
        ContentPublicationEntity publication = approvedPublication();
        when(publicationRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(publication));
        when(telegramPublisher.publish("Preview", "Home vs Away", null))
                .thenThrow(new TelegramBotApiPublisher.TelegramPublishingException("chat not found"));

        var response = service.publish(7L, "operator");

        assertEquals(PublicationStateMachine.State.FAILED, response.state());
        assertNull(response.externalPostId());
        assertEquals("FAILED", publication.getLastAttemptStatus());
        verify(attemptRepository).save(argThat(attempt -> "FAILED".equals(attempt.getStatus())));
    }

    @Test
    void refusesUnapprovedPublicationBeforeCallingTelegram() {
        ContentPublicationEntity publication = approvedPublication();
        publication.setApprovedAt(null);
        when(publicationRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(publication));

        assertThrows(ResponseStatusException.class, () -> service.publish(7L, "operator"));
        verifyNoInteractions(telegramPublisher);
    }

    private static ContentPublicationEntity approvedPublication() {
        ContentPublicationEntity publication = new ContentPublicationEntity();
        publication.setId(7L);
        publication.setPlatform(PublicationPlatform.TELEGRAM);
        publication.setState(PublicationStateMachine.State.APPROVED);
        publication.setApprovedBy("editor");
        publication.setApprovedAt(Instant.parse("2026-10-22T10:00:00Z"));
        publication.setTitle("Preview");
        publication.setBody("Home vs Away");
        publication.setAttemptCount(0);
        return publication;
    }
}
