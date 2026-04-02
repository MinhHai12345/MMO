package com.mmo.module.fb.channel.strategy.impl;

import com.mmo.module.fb.channel.model.Platform;
import com.mmo.module.fb.channel.strategy.ContentStrategy;
import com.mmo.module.fb.channel.util.TelegramUtils;
import com.mmo.module.fb.model.MatchPrediction;
import org.springframework.stereotype.Component;

@Component
public class TelegramContentStrategy implements ContentStrategy {
    @Override
    public String content(MatchPrediction prediction) {
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
                escape(prediction.getHomeName()), escape(prediction.getAwayName()),
                escape(prediction.getLeague()),
                escape(prediction.getMatchTime()),
                escape(prediction.getHomeName()), prediction.getHomeXG(),
                escape(prediction.getAwayName()), prediction.getAwayXG(),
                escape(prediction.getMatchOddsInsight().buildScoreContent()),
               "escape(prediction.getTacticalInsights())",
                "escape(prediction.getAiVerdict())",
                "(int)(prediction.getConfidence() * 100)"
        );
    }

    @Override
    public void dailyMatchesInformation() {

    }

    private String escape(String text) { return TelegramUtils.escape(text); }

    @Override
    public Platform getPlatform() {
        return Platform.TELEGRAM;
    }
}
