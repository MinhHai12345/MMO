package com.mmo.module.fb.crawler.initialize;

import com.microsoft.playwright.Page;
import com.mmo.initialize.DataInitializer;
import com.mmo.module.fb.crawler.model.Provider;
import com.mmo.module.fb.crawler.strategy.CrawlerStrategy;
import com.mmo.module.fb.crawler.strategy.CrawlerStrategyRegistry;
import com.mmo.module.fb.entity.Match;
import com.mmo.module.fb.entity.enums.MatchStatus;
import com.mmo.module.fb.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.core.annotation.Order;

import java.util.ArrayList;
import java.util.List;

//@Component
@Order(5)
@RequiredArgsConstructor
public class MatchXGInitial implements DataInitializer {
    private final MatchRepository matchRepository;
    private final CrawlerStrategyRegistry crawlerStrategyRegistry;

    @Override
    public void initialize() {
        List<Match> matches = matchRepository.findByStatusAndHomeXGIsNullAndAwayXGIsNull(MatchStatus.FINISHED);
        CrawlerStrategy strategy = crawlerStrategyRegistry.getStrategy(Provider.SOFA_SCORE);
        List<Match> updatedMatches = new ArrayList<>();
        try (Page page = strategy.createPage()) {
            for (Match match : matches) {
                match = strategy.fetchMatchXG(page, match);
                if (match != null && match.getHomeXG() != null && match.getAwayXG() != null) {
                    updatedMatches.add(match);
                }
            }
            if (CollectionUtils.isNotEmpty(updatedMatches)) {
                matchRepository.saveAll(updatedMatches);
            }
        }
    }
}



