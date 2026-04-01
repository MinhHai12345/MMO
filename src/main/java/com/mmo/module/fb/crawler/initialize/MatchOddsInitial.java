package com.mmo.module.fb.crawler.initialize;

import com.microsoft.playwright.Page;
import com.mmo.initialize.DataInitializer;
import com.mmo.module.fb.crawler.model.Provider;
import com.mmo.module.fb.crawler.strategy.CrawlerStrategy;
import com.mmo.module.fb.crawler.strategy.CrawlerStrategyRegistry;
import com.mmo.module.fb.entity.League;
import com.mmo.module.fb.entity.Match;
import com.mmo.module.fb.entity.MatchOdds;
import com.mmo.module.fb.repository.LeagueRepository;
import com.mmo.module.fb.repository.MatchOddsRepository;
import com.mmo.module.fb.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@DependsOn("matchInitial")
@RequiredArgsConstructor
public class MatchOddsInitial implements DataInitializer {
    private final LeagueRepository leagueRepository;
    private final MatchRepository matchRepository;
    private final MatchOddsRepository oddsRepository;
    private final CrawlerStrategyRegistry crawlerStrategyRegistry;

    @Override
    public void initialize() {
        List<League> leagues = leagueRepository.findAll();
        CrawlerStrategy strategy = crawlerStrategyRegistry.getStrategy(Provider.SOFA_SCORE);

        try (Page page = strategy.createPage()) {
            for (League league : leagues) {
                List<MatchOdds> newMatchOdds = new ArrayList<>();
                List<Match> matchesWithoutOdds = matchRepository.findMatchesByLeagueAndMatchOddsIsNull(league);
                for (Match match : matchesWithoutOdds) {
                    MatchOdds matchOdds = strategy.fetchMatchOddByMatchId(page, match);
                    newMatchOdds.add(matchOdds);
                }
                if (CollectionUtils.isNotEmpty(newMatchOdds)) {
                    oddsRepository.saveAll(newMatchOdds);
                }
            }
        }
    }

}
