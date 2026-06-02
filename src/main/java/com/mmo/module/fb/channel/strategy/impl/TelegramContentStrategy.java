package com.mmo.module.fb.channel.strategy.impl;

import com.mmo.module.fb.channel.model.Platform;
import com.mmo.module.fb.channel.strategy.ContentStrategy;
import com.mmo.module.fb.channel.util.TelegramUtils;
import com.mmo.module.fb.entity.MatchPrediction;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class TelegramContentStrategy implements ContentStrategy {
    @Resource
    private TemplateEngine textTemplateEngine;

    private final DateTimeFormatter ENGLISH_FORMATTER = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);

    @Override
    public String buildMatchesDashboardContent(List<MatchPrediction> freeMatches, List<MatchPrediction> vipMatches) {
        Context context = new Context();
        context.setVariable("date", LocalDate.now().format(ENGLISH_FORMATTER));
        context.setVariable("totalValue", freeMatches.size() + vipMatches.size());
        context.setVariable("freeMatches", freeMatches);
        context.setVariable("vipMatches", vipMatches);
        context.setVariable("freeSize", freeMatches.size());

        return textTemplateEngine.process("daily_dashboard", context);
    }

    @Override
    public String buildMatchesInsightsContent(Map<Integer, List<MatchPrediction>> groupedMatches) {
        double totalExposure = groupedMatches.values().stream()
                .flatMap(List::stream).mapToDouble(MatchPrediction::getSmartStakingSize)
                .sum();

        Context context = new Context();
        context.setVariable("groupedMatches", groupedMatches);
        context.setVariable("leagueName", "FIFA World Cup 2026");
        context.setVariable("totalExposure", totalExposure);

        return textTemplateEngine.process("match_insight_multi", context);
    }

    private String escape(String text) {
        return TelegramUtils.escape(text);
    }

    @Override
    public Platform getPlatform() {
        return Platform.TELEGRAM;
    }
}
