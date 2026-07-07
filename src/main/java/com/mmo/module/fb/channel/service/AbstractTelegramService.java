package com.mmo.module.fb.channel.service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.ByteArrayInputStream;

@Slf4j
public abstract class AbstractTelegramService {
    @Resource
    private TelegramClient telegramClient;

    protected void publish(String chatId, String content, byte[] imageBytes) {
        SendPhoto sendPhoto = SendPhoto.builder()
                .chatId(chatId)
                .photo(new InputFile(new ByteArrayInputStream(imageBytes), content))
                .caption(content)
                .parseMode("HTML")
                .build();
        try {
            telegramClient.execute(sendPhoto);
            Thread.sleep(10000);
            log.info("Published to channel: {}", chatId);
        } catch (TelegramApiException | InterruptedException e) {
            log.error("Telegram Error: {}", e.getMessage());
        }
    }

    protected void publish(String chatId, String content) {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(content)
                .parseMode("HTML")
                .build();
        try {
            telegramClient.execute(sendMessage);
            log.info("Published to channel: {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Telegram Error: {}", e.getMessage());
        }
    }

}
