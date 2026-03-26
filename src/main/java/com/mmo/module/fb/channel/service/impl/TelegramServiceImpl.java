package com.mmo.module.fb.channel.service.impl;

import com.mmo.configuration.AppProperties;
import com.mmo.module.fb.channel.model.Platform;
import com.mmo.module.fb.channel.service.TelegramService;
import com.mmo.module.fb.channel.strategy.ContentStrategy;
import com.mmo.module.fb.channel.strategy.ContentStrategyRegistry;
import com.mmo.module.fb.model.MatchPrediction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramServiceImpl implements TelegramService {
    private final AppProperties appProperties;
    private final TelegramClient telegramClient;
    private final ContentStrategyRegistry strategyRegistry;

    @Override
    public void publish(MatchPrediction prediction) {
        ContentStrategy contentStrategy = strategyRegistry.getStrategy(Platform.TELEGRAM);
        String content = contentStrategy.content(prediction);

        SendMessage sendMessage = SendMessage.builder()
                .chatId(appProperties.getTelegram().getChannel().getId())
                .text(content)
                .parseMode("MarkdownV2")
                .build();
        try {
            telegramClient.execute(sendMessage);
            log.info("Published to channel: {}", appProperties.getTelegram().getChannel().getId());
        } catch (TelegramApiException e) {
            log.error("Telegram Error: {}", e.getMessage());
        }
    }

    @Override
    public void publish() {
        String content = "test bot for channel";

        SendMessage sendMessage = SendMessage.builder()
                .chatId(appProperties.getTelegram().getChannel().getId())
                .text(content)
                .parseMode("MarkdownV2")
                .build();
        try {
            telegramClient.execute(sendMessage);
            log.info("Published to channel: {}", appProperties.getTelegram().getChannel().getId());
        } catch (TelegramApiException e) {
            log.error("Telegram Error: {}", e.getMessage());
        }
    }
}
