package com.mmo.module.fb.content.publish.impl;

import com.mmo.module.fb.content.entity.ContentDraftRecordEntity;
import com.mmo.module.fb.content.repository.ContentDraftRecordRepository;
import com.mmo.module.fb.content.publish.ContentPublisher;
import com.mmo.module.fb.content.publish.ContentPublishingService.CreatePublicationCommand;
import com.mmo.module.fb.content.publish.PublicationPlatform;
import com.mmo.module.fb.content.repository.ContentPublicationAttemptRepository;
import com.mmo.module.fb.content.repository.ContentPublicationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ContentPublishingServiceImplTest {

    private final ContentDraftRecordRepository draftRepository = mock(ContentDraftRecordRepository.class);
    private final ContentPublicationRepository publicationRepository = mock(ContentPublicationRepository.class);
    private final ContentPublicationAttemptRepository attemptRepository = mock(ContentPublicationAttemptRepository.class);
    private final ContentPublisher dryRunPublisher = new DryRunContentPublisher();

    private final ContentPublishingServiceImpl service = new ContentPublishingServiceImpl(
            draftRepository, publicationRepository, attemptRepository, dryRunPublisher);

    @Test
    void refusesToCreatePublicationFromDraftWithoutEditorialApproval() {
        ContentDraftRecordEntity draft = mock(ContentDraftRecordEntity.class);
        when(draft.getEditorialStatus()).thenReturn("PENDING_REVIEW");
        when(draftRepository.findById(12L)).thenReturn(Optional.of(draft));
        when(publicationRepository.findByPlatformAndIdempotencyKey(
                PublicationPlatform.TELEGRAM, "publish-12-telegram")).thenReturn(Optional.empty());

        CreatePublicationCommand command = new CreatePublicationCommand(
                12L, PublicationPlatform.TELEGRAM, "Preview", "Reviewed football preview",
                null, Instant.parse("2030-01-01T12:00:00Z"), "editor@example");

        assertThrows(ResponseStatusException.class,
                () -> service.create("publish-12-telegram", command));
        verify(publicationRepository, never()).saveAndFlush(any());
    }

    @Test
    void refusesBlankIdempotencyKey() {
        CreatePublicationCommand command = new CreatePublicationCommand(
                12L, PublicationPlatform.TELEGRAM, "Preview", "Reviewed football preview",
                null, null, "editor");

        assertThrows(ResponseStatusException.class, () -> service.create("  ", command));
        verifyNoInteractions(draftRepository);
    }
}
