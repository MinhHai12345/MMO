package com.mmo.module.fb.crawler.initialize;

import com.mmo.initialize.DataInitializer;
import com.mmo.module.fb.crawler.model.Provider;
import com.mmo.module.fb.crawler.strategy.CrawlerStrategy;
import com.mmo.module.fb.crawler.strategy.CrawlerStrategyRegistry;
import com.mmo.module.fb.entity.League;
import com.mmo.module.fb.repository.LeagueRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class LeagueInitial implements DataInitializer {
    private final LeagueRepository leagueRepository;
    private final CrawlerStrategyRegistry crawlerStrategyRegistry;

    @Override
    public void initialize() {
        List<League> leagues = leagueRepository.findAll();
        Set<Long> leagueIds = leagues.stream()
                .filter(Objects::nonNull)
                .map(League::getSofaScoreId)
                .collect(Collectors.toSet());
        CrawlerStrategy strategy = crawlerStrategyRegistry.getStrategy(Provider.SOFA_SCORE);
        List<League> fetchLeagues = strategy.fetchLeague().stream()
                .filter(Objects::nonNull)
                .filter(league -> !leagueIds.contains(league.getSofaScoreId()))
                .toList();
        if (CollectionUtils.isNotEmpty(fetchLeagues)) {
            leagueRepository.saveAll(fetchLeagues);
        }
    }
}
