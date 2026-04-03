package com.mmo.module.fb.channel.service.impl;

import com.mmo.configuration.AppProperties;
import com.mmo.module.fb.channel.model.Platform;
import com.mmo.module.fb.channel.service.AbstractTelegramService;
import com.mmo.module.fb.channel.service.TelegramService;
import com.mmo.module.fb.channel.strategy.ContentStrategy;
import com.mmo.module.fb.channel.strategy.ContentStrategyRegistry;
import com.mmo.module.fb.entity.League;
import com.mmo.module.fb.entity.Match;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramServiceImpl extends AbstractTelegramService implements TelegramService {
    private final AppProperties appProperties;
    private final ContentStrategyRegistry strategyRegistry;

    @Override
    public void notifyProcessMatches(Map<League, List<Match>> matchesByLeague) {
        ContentStrategy contentStrategy = strategyRegistry.getStrategy(Platform.TELEGRAM);
        String content = contentStrategy.buildProcessMatchesContent(matchesByLeague);
        publish(appProperties.getTelegram().getChannel().getId(), content);
    }
}
