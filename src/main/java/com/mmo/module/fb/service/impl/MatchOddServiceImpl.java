package com.mmo.module.fb.service.impl;

import com.microsoft.playwright.Page;
import com.mmo.module.fb.crawler.model.enums.Provider;
import com.mmo.module.fb.crawler.strategy.CrawlerStrategy;
import com.mmo.module.fb.crawler.strategy.CrawlerStrategyRegistry;
import com.mmo.module.fb.entity.League;
import com.mmo.module.fb.entity.Match;
import com.mmo.module.fb.entity.MatchOdds;
import com.mmo.module.fb.repository.LeagueRepository;
import com.mmo.module.fb.repository.MatchOddsRepository;
import com.mmo.module.fb.repository.MatchRepository;
import com.mmo.module.fb.service.MatchOddService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MatchOddServiceImpl implements MatchOddService {
    private final LeagueRepository leagueRepository;
    private final MatchRepository matchRepository;
    private final MatchOddsRepository oddsRepository;
    private final CrawlerStrategyRegistry crawlerStrategyRegistry;

    @Override
    public void storeAllMatchOdds() {
        List<League> leagues = leagueRepository.findByActiveIsTrue();
        CrawlerStrategy strategy = crawlerStrategyRegistry.getStrategy(Provider.SOFA_SCORE);

        try (Page page = strategy.createPage()) {
            for (League league : leagues) {
                List<MatchOdds> newMatchOdds = new ArrayList<>();
                List<Match> matchesWithoutOdds = matchRepository.findMatchesByLeagueAndMatchOddsIsNull(league);
                for (Match match : matchesWithoutOdds) {
                    MatchOdds matchOdds = strategy.fetchMatchOddsByMatch(page, match);
                    if (matchOdds != null) {
                        newMatchOdds.add(matchOdds);
                    }
                }
                if (CollectionUtils.isNotEmpty(newMatchOdds)) {
                    oddsRepository.saveAll(newMatchOdds);
                }
            }
        }
    }
}
