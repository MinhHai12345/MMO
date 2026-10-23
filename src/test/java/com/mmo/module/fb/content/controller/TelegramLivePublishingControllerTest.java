package com.mmo.module.fb.content.controller;

import com.mmo.module.fb.content.publish.telegram.TelegramLivePublishingService;
import com.mmo.module.fb.content.publish.PublicationStateMachine;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TelegramLivePublishingControllerTest {
    private final TelegramLivePublishingService service = mock(TelegramLivePublishingService.class);
    private final TelegramLivePublishingController controller = new TelegramLivePublishingController(service);

    @Test
    void rejectsMissingPublishingKeyWhenConfigured() {
        ReflectionTestUtils.setField(controller, "publishingApiKey", "very-secret");
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.publish(10L, null, "operator"));
        assertEquals(401, error.getStatusCode().value());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsLivePublishingWhenApiKeyIsNotConfigured() {
        ReflectionTestUtils.setField(controller, "publishingApiKey", "");
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.publish(10L, "whatever", "operator"));
        assertEquals(503, error.getStatusCode().value());
        verifyNoInteractions(service);
    }

    @Test
    void callsServiceOnlyWithValidKey() {
        ReflectionTestUtils.setField(controller, "publishingApiKey", "very-secret");
        var expected = new TelegramLivePublishingService.LivePublishResponse(
                10L, PublicationStateMachine.State.PUBLISHED, "123", Instant.now(), "ok");
        when(service.publish(10L, "operator")).thenReturn(expected);
        assertSame(expected, controller.publish(10L, "very-secret", "operator"));
        verify(service).publish(10L, "operator");
    }
}
