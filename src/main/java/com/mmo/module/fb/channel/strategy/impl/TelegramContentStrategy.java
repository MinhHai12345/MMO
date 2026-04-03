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


    public String content() {
        return String.format(
                "📊 *H2 FOOTBALL DATA INSIGHT* ⚽\n" +
                "────────────────────────\n\n" +
                "⚽ *%s vs %s*\n" +
                "🏆 *League:* %s\n" +
                "🕒 *Time:* %s\n\n" +
                "📉 *EXPECTED GOALS (xG) ANALYSIS*\n" +
                "• %s: %.2f\n" +
                "• %s: %.2f\n\n" +
                "🎯 *SCORE PROBABILITY*\n" +
                "%s\n\n" +
                "💡 *TACTICAL INSIGHTS*\n" +
                "%s\n\n" +
                "🚀 *AI MATCH PROJECTION*\n" +
                "Verdict: *%s*\n" +
                "Confidence: %s\n",
//                escape(prediction.getHomeName()), escape(prediction.getAwayName()),
//                escape(prediction.getLeague()),
//                escape(prediction.getMatchTime()),
//                escape(prediction.getHomeName()), prediction.getHomeXG(),
//                escape(prediction.getAwayName()), prediction.getAwayXG(),
//                escape(prediction.getMatchOddsInsight().buildScoreContent()),
                "escape(prediction.getTacticalInsights())",
                "escape(prediction.getAiVerdict())",
                "(int)(prediction.getConfidence() * 100)"
        );
    }

    @Override
    public String buildProcessMatchesContent(Map<League, List<Match>> matchesByLeague) {
        Context context = new Context();
        String date = LocalDate.now().format(VN_FORMATTER);
        context.setVariable("date", date);
        context.setVariable("matchesByLeague", matchesByLeague);

        return templateEngine.process("daily-matches-information", context);
    }

    private String escape(String text) {
        return TelegramUtils.escape(text);
    }

    @Override
    public Platform getPlatform() {
        return Platform.TELEGRAM;
    }
}
