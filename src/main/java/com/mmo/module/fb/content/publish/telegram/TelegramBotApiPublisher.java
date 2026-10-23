package com.mmo.module.fb.content.publish.telegram;

import com.mmo.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.Instant;

/**
 * Publication adapter backed by the application's shared TelegramClient bean.
 * The bot must have permission to post to the configured target channel.
 */
@Component
@RequiredArgsConstructor
public class TelegramBotApiPublisher {
    private static final int MAX_TEXT_LENGTH = 4096;
    private static final int MAX_PHOTO_CAPTION_LENGTH = 1024;

    private final TelegramClient telegramClient;
    private final AppProperties properties;

    public TelegramPublishResult publish(String title, String body, String mediaReference) {
        String chatId = properties.getTelegram().getChannel().getId();
        if (isUnconfigured(properties.getTelegram().getBot().getToken()) || isUnconfigured(chatId)) {
            throw new IllegalStateException("Telegram bot token and channel ID must be configured");
        }

        String text = (title == null || title.isBlank())
                ? (body == null ? "" : body.strip())
                : title.strip() + "\n\n" + (body == null ? "" : body.strip());
        try {
            Message sent;
            if (mediaReference != null && !mediaReference.isBlank()) {
                if (text.codePointCount(0, text.length()) > MAX_PHOTO_CAPTION_LENGTH) {
                    throw new IllegalArgumentException("Telegram photo caption must not exceed 1024 characters");
                }
                SendPhoto request = SendPhoto.builder()
                        .chatId(chatId)
                        .photo(new InputFile(mediaReference.strip()))
                        .caption(text)
                        .build();
                sent = telegramClient.execute(request);
            } else {
                if (text.codePointCount(0, text.length()) > MAX_TEXT_LENGTH) {
                    throw new IllegalArgumentException("Telegram message including title must not exceed 4096 characters");
                }
                SendMessage request = SendMessage.builder()
                        .chatId(chatId)
                        .text(text)
                        .build();
                sent = telegramClient.execute(request);
            }

            if (sent == null || sent.getMessageId() == null) {
                throw new TelegramPublishingException("Telegram response did not contain message_id");
            }
            String responseChatId = sent.getChat() == null || sent.getChat().getId() == null
                    ? chatId : sent.getChat().getId().toString();
            return new TelegramPublishResult(sent.getMessageId().toString(), responseChatId, Instant.now());
        } catch (TelegramApiException ex) {
            throw new TelegramPublishingException("Telegram Bot API request failed: " + safeMessage(ex), ex);
        }
    }

    private static boolean isUnconfigured(String value) {
        return value == null || value.isBlank() || "mock".equalsIgnoreCase(value.strip());
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return exception.getClass().getSimpleName();
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    public record TelegramPublishResult(String messageId, String chatId, Instant completedAt) {}

    public static class TelegramPublishingException extends RuntimeException {
        public TelegramPublishingException(String message) { super(message); }
        public TelegramPublishingException(String message, Throwable cause) { super(message, cause); }
    }
}
