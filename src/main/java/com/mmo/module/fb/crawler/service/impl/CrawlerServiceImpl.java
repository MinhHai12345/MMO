package com.mmo.module.fb.crawler.service.impl;

import com.microsoft.playwright.Page;
import com.mmo.module.fb.crawler.model.Provider;
import com.mmo.module.fb.crawler.service.CrawlerService;
import com.mmo.module.fb.crawler.strategy.CrawlerStrategy;
import com.mmo.module.fb.crawler.strategy.CrawlerStrategyRegistry;
import com.mmo.module.fb.entity.Match;
import com.mmo.module.fb.entity.MatchOdds;
import com.mmo.module.fb.entity.enums.MatchStatus;
import com.mmo.module.fb.repository.MatchOddsRepository;
import com.mmo.module.fb.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CrawlerServiceImpl implements CrawlerService {
    private final MatchRepository matchRepository;
    private final TemplateEngine templateEngine;
    private final MatchOddsRepository matchOddsRepository;
    private final CrawlerStrategyRegistry crawlerStrategyRegistry;

    @Override
    public void fetchDailyMatches() {
        CrawlerStrategy strategy = crawlerStrategyRegistry.getStrategy(Provider.SOFA_SCORE);
        Page page = strategy.createPage();
        Set<Long> sofaScoreIds = strategy.fetchDailyUpComingMatches(page);
        List<Match> matches = matchRepository.findBySofaScoreIdIn(sofaScoreIds);
        if (CollectionUtils.isNotEmpty(matches)) {
            matches.forEach(match -> {
                MatchOdds matchOdds = strategy.fetchMatchOddsByMatch(page, match);
                matchOddsRepository.save(matchOdds);
                match.setStatus(MatchStatus.PROCESSING);
            });
            matchRepository.saveAll(matches);
        }
    }

}
