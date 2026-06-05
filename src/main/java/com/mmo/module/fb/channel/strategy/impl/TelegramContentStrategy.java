package com.mmo.module.fb.channel.strategy.impl;

import com.mmo.module.fb.channel.model.Platform;
import com.mmo.module.fb.channel.strategy.ContentStrategy;
import com.mmo.module.fb.entity.MatchPrediction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.List;

@Component
public class TelegramContentStrategy implements ContentStrategy {

    @Autowired
    @Qualifier("textTemplateEngine")
    private SpringTemplateEngine textTemplateEngine;

    @Autowired
    @Qualifier("htmlTemplateEngine")
    private SpringTemplateEngine htmlTemplateEngine;

    @Override
    public String buildMatchesDashboardContent(List<MatchPrediction> freeMatches, List<MatchPrediction> vipMatches) {
        double totalExposure = freeMatches.stream().mapToDouble(mp -> mp.getSmartStakingSize() != null
                ? mp.getSmartStakingSize() : 0.0).sum()
                               + vipMatches.stream().mapToDouble(mp -> mp.getSmartStakingSize() != null
                ? mp.getSmartStakingSize() : 0.0).sum();
        Context context = new Context();
        context.setVariable("leagueName", "FIFA World Cup 2026");
        context.setVariable("totalValue", freeMatches.size() + vipMatches.size());
        context.setVariable("freeMatches", freeMatches);
        context.setVariable("vipMatches", vipMatches);
        context.setVariable("freeSize", freeMatches.size());
        context.setVariable("totalExposure", totalExposure);
        return htmlTemplateEngine.process("daily_dashboard", context);
    }

    @Override
    public String buildMatchesInsightsContent(List<MatchPrediction> matches) {
        double totalExposure = matches.stream().mapToDouble(MatchPrediction::getSmartStakingSize).sum();
        Context context = new Context();
        context.setVariable("matches", matches);
        context.setVariable("leagueName", "FIFA World Cup 2026");
        context.setVariable("totalExposure", totalExposure);
        return htmlTemplateEngine.process("match_insights", context);
    }

    @Override
    public String buildMatchesRecapContent(List<MatchPrediction> matches) {
        long winCount = matches.stream().filter(MatchPrediction::isWin).count();
        Context context = new Context();
        context.setVariable("leagueName", "FIFA World Cup 2026");
        context.setVariable("completedMatches", matches);
        context.setVariable("totalMatches", matches.size());
        context.setVariable("lossCount", matches.size() - winCount);
        context.setVariable("winCount", winCount);
        return htmlTemplateEngine.process("daily_recap", context);
    }

    @Override
    public Platform getPlatform() {
        return Platform.TELEGRAM;
    }
}
