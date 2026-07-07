package com.mmo.module.fb.channel.strategy.impl;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.ScreenshotType;
import com.mmo.module.fb.channel.model.Platform;
import com.mmo.module.fb.channel.model.PredictionData;
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
    public String buildMatchesInsightsContent(List<PredictionData> matches) {
        Context context = new Context();
        context.setVariable("matches", matches);
        context.setVariable("leagueName", "FIFA World Cup 2026");
        return htmlTemplateEngine.process("multi_match_insight", context);
    }

    @Override
    public byte[] buildMatchesInsightImage(PredictionData match) {
        Context context = new Context();
        context.setVariable("match", match);
        context.setVariable("leagueName", "FIFA World Cup 2026");
        String htmlContent = htmlTemplateEngine.process("quant-infographic", context);
        byte[] screenshot;
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            BrowserContext browserContext = browser.newContext();
            Page page = browserContext.newPage();
            page.setViewportSize(1080, 1920);
            page.setContent(htmlContent);
            screenshot = page.screenshot(new Page.ScreenshotOptions().setType(ScreenshotType.PNG));
            browser.close();
            return screenshot;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
