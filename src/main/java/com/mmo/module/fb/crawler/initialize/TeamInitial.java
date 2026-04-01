package com.mmo.module.fb.crawler.initialize;

import com.microsoft.playwright.Page;
import com.mmo.initialize.DataInitializer;
import com.mmo.module.fb.crawler.model.Provider;
import com.mmo.module.fb.crawler.strategy.CrawlerStrategy;
import com.mmo.module.fb.crawler.strategy.CrawlerStrategyRegistry;
import com.mmo.module.fb.entity.League;
import com.mmo.module.fb.entity.Team;
import com.mmo.module.fb.repository.LeagueRepository;
import com.mmo.module.fb.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@DependsOn("seasonInitial")
@RequiredArgsConstructor
public class TeamInitial implements DataInitializer {
    private final LeagueRepository leagueRepository;
    private final TeamRepository teamRepository;
    private final CrawlerStrategyRegistry crawlerStrategyRegistry;

    @Override
    public void initialize() {
        List<League> leagues = leagueRepository.findAll();
        CrawlerStrategy strategy = crawlerStrategyRegistry.getStrategy(Provider.SOFA_SCORE);

        Set<Long> existingSofaSeasonIds = teamRepository.findAll().stream()
                .map(Team::getSofaScoreId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Page page = strategy.createPage();

        leagues.forEach(league -> {
            List<Team> teams = strategy.fetchTeamsByLeague(page, league).stream()
                    .filter(Objects::nonNull)
                    .filter(season -> !existingSofaSeasonIds.contains(season.getSofaScoreId()))
                    .toList();

            if (CollectionUtils.isNotEmpty(teams)) {
                teamRepository.saveAll(teams);
                existingSofaSeasonIds.addAll(teams.stream().map(Team::getSofaScoreId).collect(Collectors.toSet()));
            }
        });
    }
}
