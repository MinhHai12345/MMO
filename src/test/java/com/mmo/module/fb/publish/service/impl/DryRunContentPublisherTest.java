package com.mmo.module.fb.publish.service.impl;

import com.mmo.module.fb.publish.service.ContentPublisher;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class DryRunContentPublisherTest {

    private final DryRunContentPublisher publisher = new DryRunContentPublisher();

    @Test
    void reportsDryRunWithoutInventingExternalPostId() {
        ContentPublisher.PublishCommand command = new ContentPublisher.PublishCommand(
                "draft-42:telegram:v1",
                "Match preview",
                "A data-led match preview.",
                null,
                Instant.parse("2030-01-01T12:00:00Z"));

        ContentPublisher.PublishResult result = publisher.publish(command);

        assertEquals("DRY_RUN", result.platform());
        assertEquals("DRY_RUN", result.status());
        assertNull(result.externalPostId());
        assertNotNull(result.completedAt());
        assertTrue(result.message().contains("no external platform API was called"));
    }

    @Test
    void rejectsNullCommand() {
        assertThrows(IllegalArgumentException.class, () -> publisher.publish(null));
    }
}
