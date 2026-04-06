package com.mmo.module.fb.channel.strategy.impl;

import com.mmo.module.fb.channel.model.Platform;
import com.mmo.module.fb.channel.strategy.ContentStrategy;
import com.mmo.module.fb.channel.util.TelegramUtils;
import com.mmo.module.fb.entity.League;
import com.mmo.module.fb.entity.Match;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class TelegramContentStrategy implements ContentStrategy {
    private final TemplateEngine templateEngine;

    DateTimeFormatter VN_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy", new Locale("vi", "VN"));

    @Override
    public String buildMatchesDailyContent(Map<League, List<Match>> matchesByLeague) {
        Context context = new Context();
        String date = LocalDate.now().format(VN_FORMATTER);
        context.setVariable("date", date);
        context.setVariable("matchesByLeague", matchesByLeague);
        return templateEngine.process("tele-daily-matches", context);
    }

    @Override
    public String buildMatchInsightsContent(Map<League, List<Match>> matchesByLeague) {
        Context context = new Context();
        String date = LocalDate.now().format(VN_FORMATTER);
        context.setVariable("date", date);
        context.setVariable("matchesByLeague", matchesByLeague);
        return templateEngine.process("tele-daily-insights", context);
    }

    private String escape(String text) {
        return TelegramUtils.escape(text);
    }

    @Override
    public Platform getPlatform() {
        return Platform.TELEGRAM;
    }
}
