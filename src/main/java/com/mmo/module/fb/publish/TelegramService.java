package com.mmo.module.fb.publish;

import com.mmo.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.polls.SendPoll;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.ByteArrayInputStream;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramService {

    private final TelegramClient telegramClient;

    private final AppProperties appProperties;

    public void sendMessage(String message) {
        try {
            SendMessage sendMessage = SendMessage.builder()
                    .chatId(appProperties.getTelegram().getChannel().getId())
                    .text(message)
                    .parseMode("HTML")
                    .build();
            telegramClient.execute(sendMessage);
            log.info("✅ Telegram message sent successfully");
        } catch (TelegramApiException e) {
            log.error("❌ Failed to send Telegram message: {}", e.getMessage());
        }
    }

    public void sendPhoto(String message, byte[] imageBytes) {
        try {
            SendPhoto sendPhoto = SendPhoto.builder()
                    .chatId(appProperties.getTelegram().getChannel().getId())
                    .photo(new InputFile(new ByteArrayInputStream(imageBytes), "insight.png"))
                    .caption(message)
                    .parseMode("HTML")
                    .build();
            telegramClient.execute(sendPhoto);
            log.info("✅ Telegram photo sent successfully");
        } catch (TelegramApiException e) {
            log.error("❌ Failed to send Telegram photo: {}", e.getMessage());
        }
    }

    public void notifyMatchesDashboard(List<?> freeMatches, List<?> vipMatches) {
        // TODO: Implement dashboard notification
        log.info("📊 Sending dashboard notification: {} free, {} VIP", freeMatches.size(), vipMatches.size());
    }

    public void notifyMatchesInsights(List<?> predictions) {
        // TODO: Implement insights notification
        log.info("🔮 Sending insights notification for {} matches", predictions.size());
    }

    public void notifyMatchesRecap(List<?> matches) {
        // TODO: Implement recap notification
        log.info("📈 Sending recap notification for {} matches", matches.size());
    }

    public void sendPoll(String question, List<String> options, String chatId) {
        try {
            SendPoll sendPoll = SendPoll.builder()
                    .chatId(chatId)
                    .question(question)
                    .options(options)
                    .isAnonymous(false)
                    .build();
            telegramClient.execute(sendPoll);
            log.info("✅ Telegram poll sent successfully");
        } catch (TelegramApiException e) {
            log.error("❌ Failed to send Telegram poll: {}", e.getMessage());
        }
    }

    public void sendPollToFreeChannel(String question, List<String> options) {
        sendPoll(question, options, appProperties.getTelegram().getChannel().getFree());
    }

    public void sendPollToPremiumChannel(String question, List<String> options) {
        sendPoll(question, options, appProperties.getTelegram().getChannel().getPremium());
    }

    public void sendToFreeChannel(String message) {
        try {
            SendMessage sendMessage = SendMessage.builder()
                    .chatId(appProperties.getTelegram().getChannel().getFree())
                    .text(message)
                    .parseMode("HTML")
                    .build();
            telegramClient.execute(sendMessage);
            log.info("✅ Message sent to free channel successfully");
        } catch (TelegramApiException e) {
            log.error("❌ Failed to send message to free channel: {}", e.getMessage());
        }
    }

    public void sendToPremiumChannel(String message) {
        try {
            SendMessage sendMessage = SendMessage.builder()
                    .chatId(appProperties.getTelegram().getChannel().getPremium())
                    .text(message)
                    .parseMode("HTML")
                    .build();
            telegramClient.execute(sendMessage);
            log.info("✅ Message sent to premium channel successfully");
        } catch (TelegramApiException e) {
            log.error("❌ Failed to send message to premium channel: {}", e.getMessage());
        }
    }

    public void sendPhotoToFreeChannel(String message, byte[] imageBytes) {
        try {
            SendPhoto sendPhoto = SendPhoto.builder()
                    .chatId(appProperties.getTelegram().getChannel().getFree())
                    .photo(new InputFile(new ByteArrayInputStream(imageBytes), "insight.png"))
                    .caption(message)
                    .parseMode("HTML")
                    .build();
            telegramClient.execute(sendPhoto);
            log.info("✅ Photo sent to free channel successfully");
        } catch (TelegramApiException e) {
            log.error("❌ Failed to send photo to free channel: {}", e.getMessage());
        }
    }

    public void sendPhotoToPremiumChannel(String message, byte[] imageBytes) {
        try {
            SendPhoto sendPhoto = SendPhoto.builder()
                    .chatId(appProperties.getTelegram().getChannel().getPremium())
                    .photo(new InputFile(new ByteArrayInputStream(imageBytes), "insight.png"))
                    .caption(message)
                    .parseMode("HTML")
                    .build();
            telegramClient.execute(sendPhoto);
            log.info("✅ Photo sent to premium channel successfully");
        } catch (TelegramApiException e) {
            log.error("❌ Failed to send photo to premium channel: {}", e.getMessage());
        }
    }

    public void editMessage(String chatId, int messageId, String newText) {
        try {
            EditMessageText editMessageText = EditMessageText.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .text(newText)
                    .parseMode("HTML")
                    .build();
            telegramClient.execute(editMessageText);
            log.info("✅ Message edited successfully");
        } catch (TelegramApiException e) {
            log.error("❌ Failed to edit message: {}", e.getMessage());
        }
    }
}
