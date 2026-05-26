package com.mmo.module.fb.service.impl;

import com.microsoft.playwright.Page;
import com.mmo.module.fb.crawler.model.enums.Provider;
import com.mmo.module.fb.crawler.strategy.CrawlerStrategy;
import com.mmo.module.fb.crawler.strategy.CrawlerStrategyRegistry;
import com.mmo.module.fb.entity.League;
import com.mmo.module.fb.entity.Match;
import com.mmo.module.fb.entity.Team;
import com.mmo.module.fb.entity.enums.MatchStatus;
import com.mmo.module.fb.repository.LeagueRepository;
import com.mmo.module.fb.repository.MatchRepository;
import com.mmo.module.fb.repository.TeamRepository;
import com.mmo.module.fb.service.MatchService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MatchServiceImpl implements MatchService {
    private final TeamRepository teamRepository;
    private final LeagueRepository leagueRepository;
    private final MatchRepository matchRepository;
    private final CrawlerStrategyRegistry crawlerStrategyRegistry;

    @Override
    public void storeAllMatches() {
        List<League> leagues = leagueRepository.findByActiveIsTrue();
        CrawlerStrategy strategy = crawlerStrategyRegistry.getStrategy(Provider.SOFA_SCORE);

        Set<Long> existingSofaSeasonIds = matchRepository.findAllSofaScoreIdIsNull();
        Page page = strategy.createPage();
        Map<Long, Team> teamMap = getTeamMap();
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
                    populateMatchTeam(matches, teamMap);
                    matchRepository.saveAll(newMatches);
                    existingSofaSeasonIds.addAll(newMatches.stream().map(Match::getSofaScoreId).collect(Collectors.toSet()));
                }
            }
        });
    }

    @Override
    public void storeAllMatchXGs() {
        List<Match> matches = matchRepository.findByStatusAndHomeXGIsNullAndAwayXGIsNull(MatchStatus.FINISHED);
        CrawlerStrategy strategy = crawlerStrategyRegistry.getStrategy(Provider.SOFA_SCORE);
        List<Match> updatedMatches = new ArrayList<>();
        try (Page page = strategy.createPage()) {
            for (Match match : matches) {
                match = strategy.fetchMatchXG(page, match);
                if (match != null && match.getHomeXG() != null && match.getAwayXG() != null) {
                    match.setStatus(MatchStatus.FINISHED);
                    updatedMatches.add(match);
                }
            }
            if (CollectionUtils.isNotEmpty(updatedMatches)) {
                matchRepository.saveAll(updatedMatches);
            }
        }
    }

    private Map<Long, Team> getTeamMap() {
        List<Team> teams = teamRepository.findAll();
        return CollectionUtils.isNotEmpty(teams) ? teams.stream()
                .collect(Collectors.toMap(Team::getSofaScoreId, Function.identity())) : new HashMap<>();
    }

    private void populateMatchTeam(List<Match> matches, Map<Long, Team> teamMap) {
        matches.forEach(match -> {
            match.setHomeTeam(teamMap.get(match.getSofaScoreHomeTeamId()));
            match.setAwayTeam(teamMap.get(match.getSofaScoreAwayTeamId()));
        });
    }
}
