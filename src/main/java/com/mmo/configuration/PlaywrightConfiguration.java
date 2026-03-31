package com.mmo.configuration;

import com.microsoft.playwright.Playwright;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PlaywrightConfiguration {

    @Bean(destroyMethod = "close")
    public Playwright playwright() {
        return Playwright.create();
    }
}
