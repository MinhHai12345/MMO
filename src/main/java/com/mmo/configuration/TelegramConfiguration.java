package com.mmo.configuration;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Configuration
@RequiredArgsConstructor
public class TelegramConfiguration {
    private final AppProperties appProperties;

    @Bean
    public TelegramClient telegramClient() {
        return new OkHttpTelegramClient(appProperties.getTelegram().getBot().getToken());
    }
}
