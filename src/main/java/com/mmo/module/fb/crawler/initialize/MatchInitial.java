package com.mmo.module.fb.crawler.initialize;

import com.microsoft.playwright.Page;
import com.mmo.initialize.DataInitializer;
import com.mmo.module.fb.crawler.model.Provider;
import com.mmo.module.fb.crawler.strategy.CrawlerStrategy;
import com.mmo.module.fb.crawler.strategy.CrawlerStrategyRegistry;
import com.mmo.module.fb.entity.League;
import com.mmo.module.fb.entity.Match;
import com.mmo.module.fb.repository.LeagueRepository;
import com.mmo.module.fb.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@DependsOn("teamInitial")
@RequiredArgsConstructor
public class MatchInitial implements DataInitializer {
    private final LeagueRepository leagueRepository;
    private final MatchRepository matchRepository;
    private final CrawlerStrategyRegistry crawlerStrategyRegistry;

    @Override
    public void initialize() {
        List<League> leagues = leagueRepository.findAll();
        CrawlerStrategy strategy = crawlerStrategyRegistry.getStrategy(Provider.SOFA_SCORE);

        Set<Long> existingSofaSeasonIds = matchRepository.findAll().stream()
                .map(Match::getSofaScoreId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Page page = strategy.createPage();

        leagues.forEach(league -> {
            int emptyCount = 0;
            for (int round = 1; round <= 50; round++) {
                List<Match> matches = strategy.fetchMatchesByRound(page, league, round);

                if (matches.isEmpty()) {
                    emptyCount++;
                    if (emptyCount > 3 && round > 10) {
                        break;
                    }
                    continue;
                }

                emptyCount = 0;
                List<Match> newMatches = matches.stream()
                        .filter(match -> !existingSofaSeasonIds.contains(match.getSofaScoreId()))
                        .toList();
                if (CollectionUtils.isNotEmpty(newMatches)) {
                    matchRepository.saveAll(newMatches);
                    existingSofaSeasonIds.addAll(newMatches.stream().map(Match::getSofaScoreId).collect(Collectors.toSet()));
                }
            }

        });
    }
}
