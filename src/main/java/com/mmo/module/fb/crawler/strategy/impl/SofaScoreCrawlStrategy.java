package com.mmo.module.fb.crawler.strategy.impl;

import com.microsoft.playwright.Page;
import com.mmo.converter.DynamicConverter;
import com.mmo.module.fb.crawler.model.enums.Provider;
import com.mmo.module.fb.crawler.model.sofa.SofaMatchData;
import com.mmo.module.fb.crawler.model.sofa.SofaMatchStatisticsData;
import com.mmo.module.fb.crawler.model.sofa.SofaSeasonData;
import com.mmo.module.fb.crawler.model.sofa.SofaStandingsData;
import com.mmo.module.fb.crawler.model.sofa.SofaUniqueTournamentsData;
import com.mmo.module.fb.crawler.service.SofaCrawlerService;
import com.mmo.module.fb.crawler.strategy.AbstractCrawler;
import com.mmo.module.fb.entity.League;
import com.mmo.module.fb.entity.Match;
import com.mmo.module.fb.entity.Season;
import com.mmo.module.fb.entity.Team;
import com.mmo.module.fb.entity.enums.MatchStatus;
import com.mmo.module.fb.repository.LeagueRepository;
import com.mmo.module.fb.repository.MatchRepository;
import com.mmo.module.fb.repository.SeasonRepository;
import com.mmo.module.fb.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class SofaScoreCrawlStrategy extends AbstractCrawler {
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final DynamicConverter dynamicConverter;
    private final LeagueRepository leagueRepository;
    private final SeasonRepository seasonRepository;
    private final SofaCrawlerService sofaCrawlerService;

    @Override
    public void storeLeagues() {
        Page page = createPage();
        List<SofaUniqueTournamentsData.UniqueTournamentDTO> tournaments = sofaCrawlerService.fetchLeagues(page);
        if (CollectionUtils.isNotEmpty(tournaments)) {
            List<League> leagues = leagueRepository.findAll();
            Set<Long> leagueIds = leagues.stream()
                    .filter(Objects::nonNull)
                    .map(League::getSofaScoreId)
                    .collect(Collectors.toSet());
            tournaments = tournaments.stream()
                    .filter(Objects::nonNull)
                    .filter(it -> !leagueIds.contains(it.getId()))
                    .toList();
            if (CollectionUtils.isNotEmpty(tournaments)) {
                leagueRepository.saveAll(dynamicConverter.convertAll(tournaments, League.class));
            }
        }
    }

    @Override
    public void storeSeasons() {
        List<League> leagues = leagueRepository.findByActiveIsTrue();
        Page page = createPage();
        Set<Long> existingSofaSeasonIds = seasonRepository.findAll().stream()
                .map(Season::getSofaScoreId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        leagues.forEach(league -> {
            List<SofaSeasonData.SeasonDTO> seasonDTOS = sofaCrawlerService.fetchSeasonByTournamentId(league.getSofaScoreId(), page);
            seasonDTOS.get(0).setCurrent(true);
            seasonDTOS = seasonDTOS.stream()
                    .filter(Objects::nonNull)
                    .filter(season -> !existingSofaSeasonIds.contains(season.getId()))
                    .toList();
            if (CollectionUtils.isNotEmpty(seasonDTOS)) {
                Set<Season> seasons = seasonDTOS.stream().map(seasonDTO -> {
                    Season season = dynamicConverter.convert(seasonDTO, Season.class);
                    season.setLeague(league);
                    return season;
                }).collect(Collectors.toSet());
                seasonRepository.saveAll(seasons);

                league.setSeasons(seasons);
                seasons.stream()
                        .filter(Season::isCurrent).findFirst()
                        .ifPresent(currentSeason -> league.setCurrentSeasonId(currentSeason.getId()));
                leagueRepository.save(league);
                existingSofaSeasonIds.addAll(seasons.stream().map(Season::getSofaScoreId).collect(Collectors.toSet()));
            }
        });
    }

    @Override
    public void storeTeams() {
        List<League> leagues = leagueRepository.findByActiveIsTrue();
        Page page = createPage();
        Set<Long> existingSofaTeamIds = teamRepository.findAll().stream()
                .map(Team::getSofaScoreId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        leagues.forEach(league -> {
            List<SofaStandingsData.StandingGroupDTO> standings = sofaCrawlerService.fetchStandingTotal(league.getSofaScoreId(),
                    league.getCurrentSeason().getSofaScoreId(), page);
            List<SofaStandingsData.TeamDTO> teamDTOS = standings.stream()
                    .filter(Objects::nonNull)
                    .map(SofaStandingsData.StandingGroupDTO::getRows)
                    .flatMap(List::stream)
                    .map(SofaStandingsData.StandingRowDTO::getTeam)
                    .toList();

            teamDTOS = teamDTOS.stream()
                    .filter(Objects::nonNull)
                    .filter(teamDTO -> !existingSofaTeamIds.contains(teamDTO.getId()))
                    .collect(Collectors.toMap(
                            SofaStandingsData.TeamDTO::getId,
                            team -> team,
                            (existing, replacement) -> existing
                    )).values().stream().toList();

            if (CollectionUtils.isNotEmpty(teamDTOS)) {
                List<Team> teams = dynamicConverter.convertAll(teamDTOS, Team.class);
                teamRepository.saveAll(teams);
                existingSofaTeamIds.addAll(teams.stream().map(Team::getSofaScoreId).collect(Collectors.toSet()));
            }
        });
    }

    @Override
    public void storeMatches() {
        List<League> leagues = leagueRepository.findByActiveIsTrue();
        Set<Long> existingSofaMatchIds = matchRepository.findDistinctSofaScoreIds();
        Page page = createPage();
        Map<Long, Team> teamMap = getTeamMap();
        leagues.forEach(league -> {
            int emptyCount = 0;
            for (int round = 1; round <= 50; round++) {
                List<SofaMatchData.SofaEventDTO> events = sofaCrawlerService.fetchMatchesByRound(league.getSofaScoreId(),
                        league.getCurrentSeason().getSofaScoreId(), round, page);
                if (events.isEmpty()) {
                    emptyCount++;
                    if (emptyCount > 3 && round > 10) {
                        break;
                    }
                    continue;
                }
                emptyCount = 0;
                events = events.stream()
                        .filter(eventDTO -> !existingSofaMatchIds.contains(eventDTO.getId()))
                        .toList();
                if (CollectionUtils.isNotEmpty(events)) {
                    List<Match> matches = dynamicConverter.convertAll(events, Match.class);
                    populateMatchInfo(matches, teamMap, league);
                    matchRepository.saveAll(matches);
                    existingSofaMatchIds.addAll(matches.stream().map(Match::getSofaScoreId).collect(Collectors.toSet()));
                }
            }
        });
    }

    @Override
    public void storeMatchStatistics() {
        List<Match> matches = matchRepository.findByStatusAndHomeXGIsNullAndAwayXGIsNull(MatchStatus.FINISHED);
        Page page = createPage();

        for (Match match : matches) {
            List<SofaMatchStatisticsData.PeriodStatisticsDTO> statistics = sofaCrawlerService.fetchMatchStatistics(match.getSofaScoreId(), page);
            SofaMatchStatisticsData.StatisticItemDTO statisticItemDTO = statistics.stream()
                    .filter(Objects::nonNull)
                    .map(SofaMatchStatisticsData.PeriodStatisticsDTO::getGroups)
                    .flatMap(List::stream)
                    .map(SofaMatchStatisticsData.GroupDTO::getStatisticsItems)
                    .flatMap(List::stream)
                    .filter(dto -> "expectedGoals".equalsIgnoreCase(dto.getKey()))
                    .findFirst().orElse(null);

            if (statisticItemDTO != null && statisticItemDTO.getAwayValue() != null && statisticItemDTO.getHomeValue() != null) {
                match.setStatus(MatchStatus.FINISHED);
                match.setAwayXG(statisticItemDTO.getAwayValue());
                match.setHomeXG(statisticItemDTO.getHomeValue());
                matchRepository.save(match);
            }
        }
    }

    @Override
    public Provider getProvider() {
        return Provider.SOFA_SCORE;
    }

    private Map<Long, Team> getTeamMap() {
        List<Team> teams = teamRepository.findAll();
        return CollectionUtils.isNotEmpty(teams) ? teams.stream()
                .collect(Collectors.toMap(Team::getSofaScoreId, Function.identity())) : new HashMap<>();
    }

    private void populateMatchInfo(List<Match> matches, Map<Long, Team> teamMap, League league) {
        matches.forEach(match -> {
            match.setLeague(league);
            match.setSeason(league.getCurrentSeason());
            match.setHomeTeam(teamMap.get(match.getSofaScoreHomeTeamId()));
            match.setAwayTeam(teamMap.get(match.getSofaScoreAwayTeamId()));
        });
    }
}
