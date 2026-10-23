package com.mmo.module.fb.publish.service.impl;

import com.mmo.module.fb.content.api.ApprovedContentReader;
import com.mmo.module.fb.publish.service.ContentPublisher;
import com.mmo.module.fb.publish.service.ContentPublishingService.CreatePublicationCommand;
import com.mmo.module.fb.publish.domain.PublicationPlatform;
import com.mmo.module.fb.publish.repository.ContentPublicationAttemptRepository;
import com.mmo.module.fb.publish.repository.ContentPublicationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ContentPublishingServiceImplTest {

    private final ApprovedContentReader approvedContentReader = mock(ApprovedContentReader.class);
    private final ContentPublicationRepository publicationRepository = mock(ContentPublicationRepository.class);
    private final ContentPublicationAttemptRepository attemptRepository = mock(ContentPublicationAttemptRepository.class);
    private final ContentPublisher dryRunPublisher = new DryRunContentPublisher();

    private final ContentPublishingServiceImpl service = new ContentPublishingServiceImpl(
            approvedContentReader, publicationRepository, attemptRepository, dryRunPublisher);

    @Test
    void refusesToCreatePublicationFromDraftWithoutEditorialApproval() {
        when(approvedContentReader.requireApproved(12L)).thenThrow(
                new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.CONFLICT, "Not approved"));
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
        verifyNoInteractions(approvedContentReader);
    }
}
