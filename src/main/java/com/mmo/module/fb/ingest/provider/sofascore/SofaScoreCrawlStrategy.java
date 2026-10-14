package com.mmo.module.fb.ingest.provider.sofascore;

import com.mmo.module.fb.domain.entity.League;
import com.mmo.module.fb.domain.entity.LeagueExternalId;
import com.mmo.module.fb.domain.entity.Season;
import com.mmo.module.fb.domain.entity.SeasonExternalId;
import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.domain.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.domain.repository.LeagueRepository;
import com.mmo.module.fb.domain.repository.MatchPredictionRepository;
import com.mmo.module.fb.domain.repository.MatchRepository;
import com.mmo.module.fb.domain.repository.SeasonExternalIdRepository;
import com.mmo.module.fb.domain.repository.SeasonRepository;
import com.mmo.module.fb.domain.repository.TeamRepository;
import com.mmo.module.fb.ingest.provider.model.sofa.SofaMatchesData;
import com.mmo.module.fb.ingest.provider.model.sofa.SofaSeasonData;
import com.mmo.module.fb.ingest.provider.service.SofaCrawlerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class SofaScoreCrawlStrategy extends AbstractSofaScoreProvider {
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final com.mmo.core.converter.DynamicConverter dynamicConverter;
    private final LeagueRepository leagueRepository;
    private final SeasonRepository seasonRepository;
    private final SofaCrawlerService sofaCrawlerService;
    private final MatchPredictionRepository matchPredictionRepository;
    private final LeagueExternalIdRepository leagueExternalIdRepository;
    private final SeasonExternalIdRepository seasonExternalIdRepository;

    public void syncLeagues() {
        executeSimpleStorePipeline(
                sofaCrawlerService::fetchLeagues,
                tournaments -> {
                    if (CollectionUtils.isEmpty(tournaments)) return;

                    Set<String> existingSofaIds = leagueExternalIdRepository.findAll().stream()
                            .filter(eid -> Provider.SOFA_SCORE.name().equals(eid.getProvider().name()))
                            .map(LeagueExternalId::getExternalId)
                            .collect(Collectors.toSet());

//                    List<SofaSeasonData.TournamentDTO> newTournaments = tournaments.stream()
//                            .filter(Objects::nonNull)
//                            .filter(t -> !existingSofaIds.contains(String.valueOf(t.getId())))
//                            .toList();

//                    if (CollectionUtils.isNotEmpty(newTournaments)) {
//                        List<League> leagues = dynamicConverter.convertAll(newTournaments, League.class);
//                        List<League> savedLeagues = leagueRepository.saveAll(leagues);
//
//                        List<LeagueExternalId> externalIds = savedLeagues.stream()
//                                .map(league -> LeagueExternalId.builder()
//                                        .league(league)
//                                        .provider(Provider.SOFA_SCORE)
//                                        .externalId(String.valueOf(newTournaments.stream()
//                                                .filter(t -> t.getName().equals(league.getName()))
//                                                .findFirst()
//                                                .orElseThrow()
//                                                .getId()))
//                                        .build())
//                                .toList();
//                        leagueExternalIdRepository.saveAll(externalIds);
//                        log.info("✅ Đã lưu thành công {} giải đấu mới.", newTournaments.size());
//                    }
                }
        );
    }

    public void storeSeasons() {
        List<League> activeLeagues = leagueRepository.findByActiveIsTrue();
        Set<String> existingSofaSeasonIds = seasonExternalIdRepository.findAll().stream()
                .filter(eid -> Provider.SOFA_SCORE.name().equals(eid.getProvider().name()))
                .map(SeasonExternalId::getExternalId)
                .collect(Collectors.toSet());
        executeStorePipeline(
                activeLeagues,
                (league, page) -> {
                    LeagueExternalId leagueExtId = leagueExternalIdRepository
                            .findByLeagueIdAndProvider(league.getId(), Provider.SOFA_SCORE)
                            .orElseThrow(() -> new RuntimeException("League external ID not found for SOFA_SCORE"));
                    return sofaCrawlerService.fetchSeasonByTournamentId(Long.valueOf(leagueExtId.getExternalId()), page);
                },
                (league, seasonDTOS) -> {
                    if (CollectionUtils.isEmpty(seasonDTOS)) return;

                    seasonDTOS.get(0).setCurrent(true);
                    List<SofaSeasonData.SeasonDTO> newSeasons = seasonDTOS.stream()
                            .filter(Objects::nonNull)
                            .filter(season -> !existingSofaSeasonIds.contains(String.valueOf(season.getId())))
                            .toList();

                    if (CollectionUtils.isNotEmpty(newSeasons)) {
                        List<Season> seasons = newSeasons.stream().map(seasonDTO -> {
                            Season season = dynamicConverter.convert(seasonDTO, Season.class);
                            season.setLeague(league);
                            return season;
                        }).toList();

                        List<Season> savedSeasons = seasonRepository.saveAll(seasons);

                        List<SeasonExternalId> externalIds = savedSeasons.stream()
                                .map(season -> {
                                    SofaSeasonData.SeasonDTO dto = newSeasons.stream()
                                            .filter(s -> s.getName().equals(season.getYear()))
                                            .findFirst()
                                            .orElseThrow();
                                    return SeasonExternalId.builder()
                                            .season(season)
                                            .provider(Provider.SOFA_SCORE)
                                            .externalId(String.valueOf(dto.getId()))
                                            .build();
                                })
                                .toList();
                        seasonExternalIdRepository.saveAll(externalIds);

                        existingSofaSeasonIds.addAll(externalIds.stream().map(SeasonExternalId::getExternalId).toList());
                    }
                }
        );
    }

    public void syncTeams() {
//        List<League> activeLeagues = leagueRepository.findByActiveIsTrue();
//        Set<Long> existingSofaTeamIds = teamRepository.findAll().stream()
//                .map(it -> Long.valueOf(it.getExternalId()))
//                .filter(Objects::nonNull)
//                .collect(Collectors.toSet());
//        executeStorePipeline(
//                activeLeagues,
//                (league, page) -> sofaCrawlerService.fetchStandingTotal(league.getSofaScoreId(),
//                        league.getCurrentSeason().getSofaScoreId(), page),
//                (league, standings) -> {
//                    if (CollectionUtils.isEmpty(standings)) return;
//
//                    List<SofaStandingsData.TeamDTO> teamDTOS = standings.stream()
//                            .filter(Objects::nonNull)
//                            .map(SofaStandingsData.StandingGroupDTO::getRows)
//                            .flatMap(List::stream)
//                            .map(SofaStandingsData.StandingRowDTO::getTeam)
//                            .filter(Objects::nonNull)
//                            .filter(teamDTO -> !existingSofaTeamIds.contains(teamDTO.getId()))
//                            .collect(Collectors.toMap(
//                                    SofaStandingsData.TeamDTO::getId,
//                                    team -> team,
//                                    (existing, replacement) -> existing
//                            )).values().stream().toList();
//
//                    if (CollectionUtils.isNotEmpty(teamDTOS)) {
//                        List<Team> teams = dynamicConverter.convertAll(teamDTOS, Team.class);
//                        teamRepository.saveAll(teams);
//                        existingSofaTeamIds.addAll(teams.stream().map(it -> Long.valueOf(it.getExternalId())).collect(Collectors.toSet()));
//                    }
//                }
//        );
    }

    public void storeMatches() {
//        List<League> activeLeagues = leagueRepository.findByActiveIsTrue();
//        Map<Long, Team> teamMap = getTeamMap();
//        executeStorePipeline(
//                activeLeagues,
//                (league, page) -> {
//                    java.util.List<SofaMatchesData.SofaEventDTO> allLeagueEvents = new java.util.ArrayList<>();
//                    int emptyCount = 0;
//                    for (int round = 1; round <= 50; round++) {
//                        if (page.isClosed()) {
//                            log.info("⚠️ Page bị đóng tại Round {} của giải {}. Khởi tạo lại...", round, league.getExternalId());
//                        }
//                        List<SofaMatchesData.SofaEventDTO> events = sofaCrawlerService.fetchMatchesByRound(league.getExternalId(), league.getCurrentSeason().getSofaScoreId(), round, page);
//                        if (events.isEmpty()) {
//                            emptyCount++;
//                            if (emptyCount > 3 && round > 10) break;
//                            continue;
//                        }
//                        emptyCount = 0;
//                        allLeagueEvents.addAll(events);
//                    }
//                    return allLeagueEvents;
//                },
//                (league, allEvents) -> {
//                    if (CollectionUtils.isEmpty(allEvents)) return;
//                    saveMatches(allEvents, teamMap, league);
//                }
//        );
    }

    public void storeMatchStatistics() {
//        do {
//            List<Match> targetMatches =
//                    matchRepository.findTop30ByStatusAndHomeXGIsNullAndAwayXGIsNullAndXgRetryCountLessThanOrderByXgRetryCountAsc(MatchStatus.FINISHED, 3);
//            if (CollectionUtils.isEmpty(targetMatches)) {
//                log.info("🏁 [Statistics] Hoàn thành! Không còn trận đấu nào cần cào xG nữa.");
//                break;
//            }
//            executeStorePipeline(
//                    targetMatches,
//                    (match, page) -> sofaCrawlerService.fetchMatchStatistics(match.getSofaScoreId(), page),
//                    (match, statistics) -> {
//                        if (CollectionUtils.isEmpty(statistics)) return;
//                        boolean hasXg = false;
//                        SofaMatchStatisticsData.StatisticItemDTO xGItem = statistics.stream()
//                                .filter(Objects::nonNull)
//                                .map(SofaMatchStatisticsData.PeriodStatisticsDTO::getGroups)
//                                .flatMap(List::stream)
//                                .map(SofaMatchStatisticsData.GroupDTO::getStatisticsItems)
//                                .flatMap(List::stream)
//                                .filter(dto -> "expectedGoals".equalsIgnoreCase(dto.getKey()))
//                                .findFirst().orElse(null);
//
//                        if (xGItem != null && xGItem.getAwayValue() != null && xGItem.getHomeValue() != null) {
//                            match.setStatus(MatchStatus.FINISHED);
//                            match.setAwayXG(xGItem.getAwayValue());
//                            match.setHomeXG(xGItem.getHomeValue());
//                            hasXg = true;
//                        }
//                        if (!hasXg) {
//                            match.setXgRetryCount(match.getXgRetryCount() + 1);
//                        }
//                        matchRepository.save(match);
//                    }
//            );
//        } while (true);
    }

    public void prepareMatchUpcomingDaily() {
//        List<League> activeLeagues = leagueRepository.findByActiveIsTrue();
//        executeStorePipeline(
//                activeLeagues,
//                (league, page) -> sofaCrawlerService.fetchMatchesDaily(league.getSofaScoreId(), page),
//                (league, wrapper) -> {
//                    if (wrapper == null || CollectionUtils.isEmpty(wrapper.getEvents())) return;
//                    saveMatches(wrapper.getEvents(), getTeamMap(), league);
//
//                    Set<Long> sofaMatchIds = wrapper.getEvents().stream()
//                            .filter(Objects::nonNull)
//                            .map(SofaMatchesData.SofaEventDTO::getId)
//                            .collect(Collectors.toSet());
//                    List<Match> matches = matchRepository.findBySofaScoreIdIn(sofaMatchIds).stream()
//                            .filter(Objects::nonNull)
//                            .filter(it -> it.getMatchPrediction() == null)
//                            .collect(Collectors.toList());
//                    if (CollectionUtils.isNotEmpty(matches)) {
//                        List<MatchPrediction> matchPredictions = dynamicConverter.convertAll(matches, MatchPrediction.class);
//                        populateMatchPredictionInfo(matchPredictions, wrapper.getOddsMap());
//                        matchPredictionRepository.saveAll(matchPredictions);
//                    }
//                }
//        );
    }

    public void prepareMatchResultDaily() {
//        List<MatchPrediction> targetMatches = matchPredictionRepository.findByStatus(MatchPredictionStatus.POSTED);
//        if (CollectionUtils.isEmpty(targetMatches)) {
//            return;
//        }
//        executeStorePipeline(
//                targetMatches,
//                (prediction, page) -> sofaCrawlerService.fetchMatchById(prediction.getMatch().getSofaScoreId(), page),
//                (prediction, event) -> {
//                    if (event == null) return;
//                    Integer homeScore = event.getHomeScore().getCurrent();
//                    Integer awayScore = event.getAwayScore().getCurrent();
//                    if (homeScore != null && awayScore != null) {
//                        prediction.setActualAwayGoals(homeScore);
//                        prediction.setActualHomeGoals(awayScore);
//                        prediction.setStatus(MatchPredictionStatus.RESULT);
//                        matchPredictionRepository.save(prediction);
//                    }
//                }
//        );
    }

    public List<SofaMatchesData.SofaEventDTO> getLatestHistoriesMatchesByTeamId(Long sofaTeamId) {
//        Team team = teamRepository.findBySofaScoreId(sofaTeamId);
//        if (team == null) return Collections.emptyList();
//        return executeSimpleFetchPipeline(
//                page -> sofaCrawlerService.fetchHistoriesMatchesByTeamIdAndIndex(team, 0, page));
        return Collections.emptyList();
    }

    public Provider getProvider() {
        return Provider.SOFA_SCORE;
    }

//    private void saveMatches(List<SofaMatchesData.SofaEventDTO> allEvents, Map<Long, Team> teamMap, League league) {
//        Set<Long> existingSofaMatchIds = matchRepository.findDistinctSofaScoreIdsAndLeague(league);
//
//        List<SofaMatchesData.SofaEventDTO> newEvents = allEvents.stream()
//                .filter(eventDTO -> !existingSofaMatchIds.contains(eventDTO.getId()))
//                .toList();
//        if (CollectionUtils.isNotEmpty(newEvents)) {
//            List<Match> matches = dynamicConverter.convertAll(newEvents, Match.class);
//            populateMatchInfo(matches, teamMap, league);
//            matchRepository.saveAll(matches);
//        }
//    }

//    private Map<Long, Team> getTeamMap() {
//        List<Team> teams = teamRepository.findAll();
//        return CollectionUtils.isNotEmpty(teams) ? teams.stream()
//                .collect(Collectors.toMap(Team::getSofaScoreId, Function.identity())) : new HashMap<>();
//    }

//    private void populateMatchInfo(List<Match> matches, Map<Long, Team> teamMap, League league) {
//        matches.forEach(match -> {
//            match.setLeague(league);
//            match.setSeason(league.getCurrentSeason());
//            match.setHomeTeam(teamMap.get(match.getSofaScoreHomeTeamId()));
//            match.setAwayTeam(teamMap.get(match.getSofaScoreAwayTeamId()));
//        });
//    }

//    private void populateMatchPredictionInfo(List<MatchPrediction> matchPredictions, Map<Long, SofaOddsData.MatchOddDetailDTO> oddsMap) {
//        matchPredictions.forEach(match -> {
//            SofaOddsData.MatchOddDetailDTO odd = oddsMap.get(match.getMatch().getSofaScoreId());
//            if (odd != null) {
//                if ("Full time".equalsIgnoreCase(odd.getMarketName())) {
//                    List<SofaOddsData.ChoiceDTO> choices = odd.getChoices();
//                    match.setSofaHomeOdd(parseFractional(choices.get(0).getFractionalValue()));
//                    match.setSofaDrawOdd(parseFractional(choices.get(1).getFractionalValue()));
//                    match.setSofaAwayOdd(parseFractional(choices.get(2).getFractionalValue()));
//                }
//            }
//        });
//    }
//
//    private double parseFractional(String fraction) {
//        if (StringUtils.isBlank(fraction) || !fraction.contains("/")) return 0L;
//        String[] parts = fraction.split("/");
//        return round(Double.parseDouble(parts[0]) / Double.parseDouble(parts[1])) + 1;
//    }
//
//    private double round(double value) {
//        long factor = (long) Math.pow(10, 2);
//        return (double) Math.round(value * factor) / factor;
//    }

}
