package com.mmo.module.fb.content.production.telegram;

import com.mmo.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TelegramBotApiPublisherTest {
    private TelegramClient telegramClient;
    private AppProperties properties;
    private TelegramBotApiPublisher publisher;

    @BeforeEach
    void setUp() {
        telegramClient = mock(TelegramClient.class);
        properties = new AppProperties();
        properties.getTelegram().getBot().setToken("test-token");
        properties.getTelegram().getChannel().setId("@football_test");
        publisher = new TelegramBotApiPublisher(telegramClient, properties);
    }

    @Test
    void sendsTextThroughSharedTelegramClientAndReturnsMessageId() throws Exception {
        Message response = telegramMessage(123, -100123L);
        when(telegramClient.execute(any(SendMessage.class))).thenReturn(response);

        var result = publisher.publish("Match preview", "Home vs Away", null);

        assertEquals("123", result.messageId());
        assertEquals("-100123", result.chatId());
        var captor = org.mockito.ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        assertEquals("@football_test", captor.getValue().getChatId());
        assertEquals("Match preview\n\nHome vs Away", captor.getValue().getText());
        verifyNoMoreInteractions(telegramClient);
    }

    @Test
    void sendsPhotoThroughSharedTelegramClient() throws Exception {
        when(telegramClient.execute(any(SendPhoto.class))).thenReturn(telegramMessage(124, -100123L));

        var result = publisher.publish("Preview", "Home vs Away", "AgAC-file-id");

        assertEquals("124", result.messageId());
        var captor = org.mockito.ArgumentCaptor.forClass(SendPhoto.class);
        verify(telegramClient).execute(captor.capture());
        assertEquals("@football_test", captor.getValue().getChatId());
        assertEquals("Preview\n\nHome vs Away", captor.getValue().getCaption());
        assertNotNull(captor.getValue().getPhoto());
    }

    @Test
    void rejectsUnconfiguredCredentialsBeforeCallingClient() {
        properties.getTelegram().getBot().setToken("mock");
        assertThrows(IllegalStateException.class, () -> publisher.publish("Title", "Body", null));
        verifyNoInteractions(telegramClient);
    }

    @Test
    void propagatesTelegramApiFailureAsRuntimeFailure() throws Exception {
        when(telegramClient.execute(any(SendMessage.class))).thenThrow(new TelegramApiException("chat not found"));

        var failure = assertThrows(TelegramBotApiPublisher.TelegramPublishingException.class,
                () -> publisher.publish("Title", "Body", null));

        assertTrue(failure.getMessage().contains("chat not found"));
    }

    @Test
    void rejectsPhotoCaptionOverTelegramLimitBeforeCallingClient() {
        assertThrows(IllegalArgumentException.class,
                () -> publisher.publish("title", "x".repeat(1024), "AgAC-file-id"));
        verifyNoInteractions(telegramClient);
    }

    private static Message telegramMessage(Integer messageId, Long chatId) {
        Message message = new Message();
        message.setMessageId(messageId);
        Chat chat = new Chat();
        chat.setId(chatId);
        message.setChat(chat);
        return message;
    }
}
