package com.mmo.module.fb.service.impl;

import com.mmo.module.fb.crawler.model.enums.Provider;
import com.mmo.module.fb.crawler.strategy.CrawlerStrategy;
import com.mmo.module.fb.crawler.strategy.CrawlerStrategyRegistry;
import com.mmo.module.fb.entity.League;
import com.mmo.module.fb.repository.LeagueRepository;
import com.mmo.module.fb.service.LeagueService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeagueServiceImpl implements LeagueService {
    private final LeagueRepository leagueRepository;
    private final CrawlerStrategyRegistry crawlerStrategyRegistry;

    @Override
    public void storeAllLeagues() {
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
