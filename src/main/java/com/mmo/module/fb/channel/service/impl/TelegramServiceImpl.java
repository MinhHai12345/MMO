package com.mmo.module.fb.channel.service.impl;

import com.mmo.configuration.AppProperties;
import com.mmo.module.fb.channel.model.Platform;
import com.mmo.module.fb.channel.service.AbstractTelegramService;
import com.mmo.module.fb.channel.service.TelegramService;
import com.mmo.module.fb.channel.strategy.ContentStrategy;
import com.mmo.module.fb.channel.strategy.ContentStrategyRegistry;
import com.mmo.module.fb.entity.MatchPrediction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramServiceImpl extends AbstractTelegramService implements TelegramService {
    private final AppProperties appProperties;
    private final ContentStrategyRegistry strategyRegistry;


    @Override
    public void notifyMatchesDashboard(List<MatchPrediction> freeMatches, List<MatchPrediction> vipMatches) {
        ContentStrategy contentStrategy = strategyRegistry.getStrategy(Platform.TELEGRAM);
        String content = contentStrategy.buildMatchesDashboardContent(freeMatches, vipMatches);
        publish(appProperties.getTelegram().getChannel().getFree(), content);
    }

    @Override
    public void notifyMatchesInsights(List<MatchPrediction> matches) {
        ContentStrategy contentStrategy = strategyRegistry.getStrategy(Platform.TELEGRAM);
        String content = contentStrategy.buildMatchesInsightsContent(matches);
        publish(appProperties.getTelegram().getChannel().getFree(), content);
    }

    @Override
    public void notifyMatchesRecap(List<MatchPrediction> matches) {
        ContentStrategy contentStrategy = strategyRegistry.getStrategy(Platform.TELEGRAM);
        String content = contentStrategy.buildMatchesRecapContent(matches);
        publish(appProperties.getTelegram().getChannel().getFree(), content);
    }

}
