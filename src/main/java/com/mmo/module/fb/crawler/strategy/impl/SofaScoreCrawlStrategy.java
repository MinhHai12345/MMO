package com.mmo.module.fb.crawler.strategy.impl;

import com.mmo.converter.DynamicConverter;
import com.mmo.module.fb.crawler.model.enums.Provider;
import com.mmo.module.fb.crawler.model.sofa.SofaDailyMatchWrapper;
import com.mmo.module.fb.crawler.model.sofa.SofaMatchData;
import com.mmo.module.fb.crawler.model.sofa.SofaMatchStatisticsData;
import com.mmo.module.fb.crawler.model.sofa.SofaOddsData;
import com.mmo.module.fb.crawler.model.sofa.SofaSeasonData;
import com.mmo.module.fb.crawler.model.sofa.SofaStandingsData;
import com.mmo.module.fb.crawler.service.SofaCrawlerService;
import com.mmo.module.fb.crawler.strategy.AbstractCrawler;
import com.mmo.module.fb.entity.League;
import com.mmo.module.fb.entity.Match;
import com.mmo.module.fb.entity.MatchPrediction;
import com.mmo.module.fb.entity.Season;
import com.mmo.module.fb.entity.Team;
import com.mmo.module.fb.entity.enums.MatchStatus;
import com.mmo.module.fb.repository.LeagueRepository;
import com.mmo.module.fb.repository.MatchPredictionRepository;
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
    private final MatchPredictionRepository matchPredictionRepository;

    @Override
    public void storeLeagues() {
        executeSimpleStorePipeline(
                sofaCrawlerService::fetchLeagues,
                tournaments -> {
                    if (CollectionUtils.isEmpty(tournaments)) return;

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
                        log.info("✅ Đã lưu thành công {} giải đấu mới.", tournaments.size());
                    }
                }
        );
    }

    @Override
    public void storeSeasons() {
        List<League> activeLeagues = leagueRepository.findByActiveIsTrue();
        Set<Long> existingSofaSeasonIds = seasonRepository.findAll().stream()
                .map(Season::getSofaScoreId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        executeStorePipeline(
                activeLeagues,
                (league, page) -> sofaCrawlerService.fetchSeasonByTournamentId(league.getSofaScoreId(), page),
                (league, seasonDTOS) -> {
                    if (CollectionUtils.isEmpty(seasonDTOS)) return;

                    seasonDTOS.get(0).setCurrent(true);
                    List<SofaSeasonData.SeasonDTO> newSeasons = seasonDTOS.stream()
                            .filter(Objects::nonNull)
                            .filter(season -> !existingSofaSeasonIds.contains(season.getId()))
                            .toList();

                    if (CollectionUtils.isNotEmpty(newSeasons)) {
                        Set<Season> seasons = newSeasons.stream().map(seasonDTO -> {
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
                }
        );
    }

    @Override
    public void storeTeams() {
        List<League> activeLeagues = leagueRepository.findByActiveIsTrue();
        Set<Long> existingSofaTeamIds = teamRepository.findAll().stream()
                .map(Team::getSofaScoreId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        executeStorePipeline(
                activeLeagues,
                (league, page) -> sofaCrawlerService.fetchStandingTotal(league.getSofaScoreId(),
                        league.getCurrentSeason().getSofaScoreId(), page),
                (league, standings) -> {
                    if (CollectionUtils.isEmpty(standings)) return;

                    List<SofaStandingsData.TeamDTO> teamDTOS = standings.stream()
                            .filter(Objects::nonNull)
                            .map(SofaStandingsData.StandingGroupDTO::getRows)
                            .flatMap(List::stream)
                            .map(SofaStandingsData.StandingRowDTO::getTeam)
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
                }
        );
    }

    @Override
    public void storeMatches() {
        List<League> activeLeagues = leagueRepository.findByActiveIsTrue();
        Set<Long> existingSofaMatchIds = matchRepository.findDistinctSofaScoreIds();
        Map<Long, Team> teamMap = getTeamMap();
        executeStorePipeline(
                activeLeagues,
                (league, page) -> {
                    java.util.List<SofaMatchData.SofaEventDTO> allLeagueEvents = new java.util.ArrayList<>();
                    int emptyCount = 0;
                    for (int round = 1; round <= 50; round++) {
                        if (page.isClosed()) {
                            log.info("⚠️ Page bị đóng tại Round {} của giải {}. Khởi tạo lại...", round, league.getSofaScoreId());
                        }
                        List<SofaMatchData.SofaEventDTO> events = sofaCrawlerService.fetchMatchesByRound(league.getSofaScoreId(), league.getCurrentSeason().getSofaScoreId(), round, page);
                        if (events.isEmpty()) {
                            emptyCount++;
                            if (emptyCount > 3 && round > 10) break;
                            continue;
                        }
                        emptyCount = 0;
                        allLeagueEvents.addAll(events);
                    }
                    return allLeagueEvents;
                },
                (league, allEvents) -> {
                    if (CollectionUtils.isEmpty(allEvents)) return;

                    List<SofaMatchData.SofaEventDTO> newEvents = allEvents.stream()
                            .filter(eventDTO -> !existingSofaMatchIds.contains(eventDTO.getId()))
                            .toList();

                    if (CollectionUtils.isNotEmpty(newEvents)) {
                        List<Match> matches = dynamicConverter.convertAll(newEvents, Match.class);
                        populateMatchInfo(matches, teamMap, league);
                        matchRepository.saveAll(matches);
                        existingSofaMatchIds.addAll(matches.stream().map(Match::getSofaScoreId).collect(Collectors.toSet()));
                    }
                }
        );
    }

    @Override
    public void storeMatchStatistics() {
        do {
            List<Match> targetMatches =
                    matchRepository.findTop30ByStatusAndHomeXGIsNullAndAwayXGIsNullAndXgRetryCountLessThanOrderByXgRetryCountAsc(MatchStatus.FINISHED, 3);
            if (CollectionUtils.isEmpty(targetMatches)) {
                log.info("🏁 [Statistics] Hoàn thành! Không còn trận đấu nào cần cào xG nữa.");
                break;
            }
            executeStorePipeline(
                    targetMatches,
                    (match, page) -> sofaCrawlerService.fetchMatchStatistics(match.getSofaScoreId(), page),
                    (match, statistics) -> {
                        if (CollectionUtils.isEmpty(statistics)) return;
                        boolean hasXg = false;
                        SofaMatchStatisticsData.StatisticItemDTO xGItem = statistics.stream()
                                .filter(Objects::nonNull)
                                .map(SofaMatchStatisticsData.PeriodStatisticsDTO::getGroups)
                                .flatMap(List::stream)
                                .map(SofaMatchStatisticsData.GroupDTO::getStatisticsItems)
                                .flatMap(List::stream)
                                .filter(dto -> "expectedGoals".equalsIgnoreCase(dto.getKey()))
                                .findFirst().orElse(null);

                        if (xGItem != null && xGItem.getAwayValue() != null && xGItem.getHomeValue() != null) {
                            match.setStatus(MatchStatus.FINISHED);
                            match.setAwayXG(xGItem.getAwayValue());
                            match.setHomeXG(xGItem.getHomeValue());
                            hasXg = true;
                        }
                        if (!hasXg) {
                            match.setXgRetryCount(match.getXgRetryCount() + 1);
                        }
                        matchRepository.save(match);
                    }
            );
        } while (true);
    }

    @Override
    public void prepareMatchUpcomingDaily() {
        List<League> activeLeagues = leagueRepository.findByActiveIsTrue();
        executeStorePipeline(
                activeLeagues,
                (league, page) -> {
                    List<SofaMatchData.SofaEventDTO> events = sofaCrawlerService.fetchMatchesDailyByTournamentId(league.getSofaScoreId(), page);
                    Map<String, SofaOddsData.MatchOddDetailDTO> odds = sofaCrawlerService.fetchDailyMatchOdds(page);
                    return new SofaDailyMatchWrapper(events, odds);
                },
                (league, wrapper) -> {
                    if (wrapper == null || CollectionUtils.isEmpty(wrapper.getEvents())) return;

                    sofaCrawlerService.fetchDailyMatchOdds(createPage());
                    Set<Long> sofaMatchIds = wrapper.getEvents().stream()
                            .filter(Objects::nonNull)
                            .map(SofaMatchData.SofaEventDTO::getId)
                            .collect(Collectors.toSet());
                    List<Match> matches = matchRepository.findBySofaScoreIdIn(sofaMatchIds);
                    if (CollectionUtils.isNotEmpty(matches)) {
                        List<MatchPrediction> matchPredictions = dynamicConverter.convertAll(matches, MatchPrediction.class);
                        // populate odds

                        matchPredictionRepository.saveAll(matchPredictions);
                    }
                }
        );
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
