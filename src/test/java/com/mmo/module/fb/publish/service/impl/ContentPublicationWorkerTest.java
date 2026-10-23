package com.mmo.module.fb.publish.service.impl;

import com.mmo.module.fb.publish.domain.PublicationPlatform;
import com.mmo.module.fb.publish.domain.PublicationStateMachine;
import com.mmo.module.fb.publish.provider.telegram.TelegramLivePublishingService;
import com.mmo.module.fb.publish.repository.ContentPublicationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContentPublicationWorkerTest {

    private final ContentPublicationRepository repository = mock(ContentPublicationRepository.class);
    private final TelegramLivePublishingService telegramService = mock(TelegramLivePublishingService.class);

    @Test
    void publishesOnlyDueTelegramRowsReturnedByRepository() {
        when(repository.findDuePublicationIds(
                eq(PublicationPlatform.TELEGRAM),
                eq(List.of(PublicationStateMachine.State.APPROVED, PublicationStateMachine.State.SCHEDULED)),
                eq(PublicationStateMachine.State.FAILED),
                any(Instant.class), any(Instant.class), eq(5), any(Pageable.class)))
                .thenReturn(List.of(12L, 13L));

        ContentPublicationWorker worker = new ContentPublicationWorker(repository, telegramService);
        worker.processDuePublications();

        verify(telegramService).publish(12L, "fb-publication-worker");
        verify(telegramService).publish(13L, "fb-publication-worker");
        verifyNoMoreInteractions(telegramService);
    }

    @Test
    void doesNothingWhenThereAreNoDuePublications() {
        when(repository.findDuePublicationIds(
                any(), anyList(), any(), any(), any(), anyInt(), any(Pageable.class)))
                .thenReturn(List.of());

        ContentPublicationWorker worker = new ContentPublicationWorker(repository, telegramService);
        worker.processDuePublications();

        verifyNoInteractions(telegramService);
    }

    @Test
    void continuesBatchWhenOnePublicationThrows() {
        when(repository.findDuePublicationIds(
                any(), anyList(), any(), any(), any(), anyInt(), any(Pageable.class)))
                .thenReturn(List.of(12L, 13L));
        when(telegramService.publish(12L, "fb-publication-worker"))
                .thenThrow(new IllegalStateException("simulated failure"));
        when(telegramService.publish(13L, "fb-publication-worker"))
                .thenReturn(new TelegramLivePublishingService.LivePublishResponse(
                        13L, PublicationStateMachine.State.PUBLISHED, "ext-13", Instant.now(), "ok"));

        ContentPublicationWorker worker = new ContentPublicationWorker(repository, telegramService);
        worker.processDuePublications();

        verify(telegramService).publish(12L, "fb-publication-worker");
        verify(telegramService).publish(13L, "fb-publication-worker");
    }
}
