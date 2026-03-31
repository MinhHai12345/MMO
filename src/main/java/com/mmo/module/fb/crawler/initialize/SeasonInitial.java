package com.mmo.module.fb.crawler.initialize;

import com.mmo.initialize.DataInitializer;
import com.mmo.module.fb.crawler.model.Provider;
import com.mmo.module.fb.crawler.strategy.CrawlerStrategy;
import com.mmo.module.fb.crawler.strategy.CrawlerStrategyRegistry;
import com.mmo.module.fb.entity.League;
import com.mmo.module.fb.entity.Season;
import com.mmo.module.fb.repository.LeagueRepository;
import com.mmo.module.fb.repository.SeasonRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@DependsOn("leagueInitial")
@RequiredArgsConstructor
public class SeasonInitial implements DataInitializer {
    private final LeagueRepository leagueRepository;
    private final SeasonRepository seasonRepository;
    private final CrawlerStrategyRegistry crawlerStrategyRegistry;

    @Override
    public void initialize() {
        List<League> leagues = leagueRepository.findAll();
        CrawlerStrategy strategy = crawlerStrategyRegistry.getStrategy(Provider.SOFA_SCORE);

        Set<Long> existingSofaSeasonIds = seasonRepository.findAll().stream()
                .map(Season::getSofaScoreId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        leagues.forEach(league -> {
            List<Season> seasons = strategy.fetchSeasonByLeague(league).stream()
                    .filter(Objects::nonNull)
                    .filter(season -> !existingSofaSeasonIds.contains(season.getSofaScoreId()))
                    .toList();

            if (CollectionUtils.isNotEmpty(seasons)) {
                seasonRepository.saveAll(seasons);
                league.setSeasons(new HashSet<>(seasons));
                Long currentSofaScoreId = seasons.stream()
                        .filter(Season::isCurrent).findFirst()
                        .map(Season::getId)
                        .orElseGet(() -> CollectionUtils.isNotEmpty(seasons) ? seasons.get(0).getId() : null);
                if (currentSofaScoreId != null) {
                    league.setCurrentSeasonId(currentSofaScoreId);
                }
                leagueRepository.save(league);
            }
        });
    }
}
