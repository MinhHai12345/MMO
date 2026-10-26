package com.mmo.module.fb.ingest.provider.sofascore;

import com.mmo.module.fb.league.entity.League;
import com.mmo.module.fb.league.entity.LeagueExternalId;
import com.mmo.module.fb.match.entity.Match;
import com.mmo.module.fb.match.entity.MatchAdvancedStats;
import com.mmo.module.fb.match.entity.MatchExternalId;
import com.mmo.module.fb.league.entity.Season;
import com.mmo.module.fb.league.entity.SeasonExternalId;
import com.mmo.module.fb.match.enums.MatchStatus;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.league.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.league.repository.LeagueRepository;
import com.mmo.module.fb.match.repository.MatchAdvancedStatsRepository;
import com.mmo.module.fb.match.repository.MatchExternalIdRepository;
import com.mmo.module.fb.analytics.repository.MatchPredictionRepository;
import com.mmo.module.fb.match.repository.MatchRepository;
import com.mmo.module.fb.league.repository.SeasonExternalIdRepository;
import com.mmo.module.fb.league.repository.SeasonRepository;
import com.mmo.module.fb.team.repository.TeamRepository;
import com.mmo.module.fb.ingest.provider.model.sofa.SofaMatchStatisticsData;
import com.mmo.module.fb.ingest.provider.model.sofa.SofaMatchesData;
import com.mmo.module.fb.ingest.provider.model.sofa.SofaSeasonData;
import com.mmo.module.fb.ingest.provider.service.SofaCrawlerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private final MatchExternalIdRepository matchExternalIdRepository;
    private final MatchAdvancedStatsRepository matchAdvancedStatsRepository;

    private static final Pattern NUMERIC_VALUE_PATTERN = Pattern.compile("-?\\d+(?:[.,]\\d+)?");

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
        List<League> activeLeagues = leagueRepository.findByIsActiveTrue();
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
        List<Match> targetMatches = matchRepository.findFinishedMatchesMissingAdvancedStats(
                MatchStatus.FINISHED, Provider.SOFA_SCORE,
                Instant.now().minus(24, ChronoUnit.HOURS), PageRequest.of(0, 30));

        if (CollectionUtils.isEmpty(targetMatches)) {
            log.debug("No finished matches are missing SofaScore advanced statistics");
            return;
        }

        log.info("Fetching advanced statistics for {} finished matches", targetMatches.size());
        executeStorePipeline(
                targetMatches,
                (match, page) -> {
                    Optional<MatchExternalId> externalId = matchExternalIdRepository
                            .findByMatchIdAndProvider(match.getId(), Provider.SOFA_SCORE);
                    if (externalId.isEmpty()) {
                        return Collections.emptyList();
                    }
                    try {
                        List<SofaMatchStatisticsData.PeriodStatisticsDTO> statistics =
                                sofaCrawlerService.fetchMatchStatistics(
                                        Long.valueOf(externalId.get().getExternalId()), page);
                        return statistics == null ? Collections.emptyList() : statistics;
                    } catch (NumberFormatException exception) {
                        log.warn("Invalid SofaScore match ID mapping for matchId={}", match.getId());
                        return Collections.emptyList();
                    }
                },
                this::persistMatchStatistics
        );
    }

    private void persistMatchStatistics(Match match,
                                       List<SofaMatchStatisticsData.PeriodStatisticsDTO> periods) {
        if (match == null) {
            return;
        }
        match.setStatisticsLastAttemptAt(Instant.now());
        matchRepository.save(match);
        if (CollectionUtils.isEmpty(periods)) {
            return;
        }

        List<SofaMatchStatisticsData.StatisticItemDTO> items = periods.stream()
                .filter(Objects::nonNull)
                .filter(period -> "ALL".equalsIgnoreCase(period.getPeriod()))
                .findFirst()
                .map(SofaMatchStatisticsData.PeriodStatisticsDTO::getGroups)
                .orElseGet(() -> periods.stream()
                        .filter(Objects::nonNull)
                        .filter(period -> CollectionUtils.isNotEmpty(period.getGroups()))
                        .findFirst()
                        .map(SofaMatchStatisticsData.PeriodStatisticsDTO::getGroups)
                        .orElse(Collections.emptyList()))
                .stream()
                .filter(Objects::nonNull)
                .map(SofaMatchStatisticsData.GroupDTO::getStatisticsItems)
                .filter(CollectionUtils::isNotEmpty)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .toList();

        if (items.isEmpty()) {
            return;
        }

        BigDecimal homeXg = statisticValue(items, true, "expectedGoals", "xg");
        BigDecimal awayXg = statisticValue(items, false, "expectedGoals", "xg");
        boolean matchChanged = false;
        if (homeXg != null && (match.getHomeXG() == null
                || Double.compare(homeXg.doubleValue(), match.getHomeXG()) != 0)) {
            match.setHomeXG(homeXg.doubleValue());
            matchChanged = true;
        }
        if (awayXg != null && (match.getAwayXG() == null
                || Double.compare(awayXg.doubleValue(), match.getAwayXG()) != 0)) {
            match.setAwayXG(awayXg.doubleValue());
            matchChanged = true;
        }
        if (matchChanged) {
            matchRepository.save(match);
        }

        MatchAdvancedStats homeStats = upsertTeamStatistics(match, match.getHomeTeam(), items, true);
        MatchAdvancedStats awayStats = upsertTeamStatistics(match, match.getAwayTeam(), items, false);
        // If xG was received, persist a per-team snapshot even when the provider does
        // not expose the other advanced fields. This records that the event was processed
        // and prevents repeatedly fetching a match whose source simply lacks those fields.
        boolean hasMatchXg = match.getHomeXG() != null || match.getAwayXG() != null;
        List<MatchAdvancedStats> statsToSave = new ArrayList<>(2);
        if (hasAdvancedMetrics(homeStats) || hasMatchXg) {
            statsToSave.add(homeStats);
        }
        if (hasAdvancedMetrics(awayStats) || hasMatchXg) {
            statsToSave.add(awayStats);
        }
        if (!statsToSave.isEmpty()) {
            matchAdvancedStatsRepository.saveAll(statsToSave);
        }
    }

    private MatchAdvancedStats upsertTeamStatistics(Match match,
                                                     com.mmo.module.fb.team.entity.Team team,
                                                     List<SofaMatchStatisticsData.StatisticItemDTO> items,
                                                     boolean homeSide) {
        MatchAdvancedStats stats = matchAdvancedStatsRepository
                .findByMatchIdAndTeamId(match.getId(), team.getId())
                .orElseGet(() -> MatchAdvancedStats.builder().match(match).team(team).build());

        setDecimalIfPresent(items, homeSide, value -> stats.setPpda(value),
                "ppda", "opponentHalfPressuresPerDefensiveAction");
        setIntegerIfPresent(items, homeSide, value -> stats.setPpdaAtt(value), "ppdaAtt");
        setIntegerIfPresent(items, homeSide, value -> stats.setPpdaDef(value), "ppdaDef");
        setDecimalIfPresent(items, homeSide, value -> stats.setFieldTilt(value), "fieldTilt");
        setDecimalIfPresent(items, homeSide, value -> stats.setXgChain(value), "xgChain");
        setDecimalIfPresent(items, homeSide, value -> stats.setXgBuildup(value), "xgBuildup");
        setDecimalIfPresent(items, homeSide, value -> stats.setPossession(value), "ballPossession", "possession");
        setIntegerIfPresent(items, homeSide, value -> stats.setDeepCompletions(value), "deepCompletions");
        setIntegerIfPresent(items, homeSide, value -> stats.setFinalThirdEntries(value), "finalThirdEntries");
        setIntegerIfPresent(items, homeSide, value -> stats.setShotsFromBox(value),
                "shotsInsideBox", "shotsFromInsideTheBox", "shotsFromBox");
        setIntegerIfPresent(items, homeSide, value -> stats.setShotsOutsideBox(value),
                "shotsOutsideBox", "shotsFromOutsideTheBox");
        setDecimalIfPresent(items, homeSide, value -> stats.setSetPieceXg(value),
                "setPieceXg", "setPieceExpectedGoals");
        setDecimalIfPresent(items, homeSide, value -> stats.setCounterAttackXg(value),
                "counterAttackXg", "counterAttackExpectedGoals");
        setIntegerIfPresent(items, homeSide, stats::setTotalShots, "totalShots", "shotsTotal", "totalShotsAttempted");
        setIntegerIfPresent(items, homeSide, stats::setShotsOnTarget, "shotsOnTarget", "shotsOnGoal", "shotsOnTargetTotal");
        setIntegerIfPresent(items, homeSide, stats::setShotsOffTarget, "shotsOffTarget", "shotsOffGoal", "shotsOffTargetTotal");
        setIntegerIfPresent(items, homeSide, stats::setBlockedShots, "blockedShots", "blockedShotsTotal");
        setIntegerIfPresent(items, homeSide, stats::setCornerKicks, "cornerKicks", "corners", "cornerKicksTotal");
        setIntegerIfPresent(items, homeSide, stats::setFouls, "fouls", "totalFouls");
        setIntegerIfPresent(items, homeSide, stats::setYellowCards, "yellowCards", "yellowCardsTotal");
        setIntegerIfPresent(items, homeSide, stats::setRedCards, "redCards", "redCardsTotal");
        setIntegerIfPresent(items, homeSide, stats::setOffsides, "offsides", "offside");
        setIntegerIfPresent(items, homeSide, stats::setTotalPasses, "totalPasses", "passes");
        setIntegerIfPresent(items, homeSide, stats::setAccuratePasses, "accuratePasses", "passesAccurate");
        setDecimalIfPresent(items, homeSide, stats::setPassAccuracy, "accuratePassesPercentage", "passAccuracy", "passesAccuracy");
        setIntegerIfPresent(items, homeSide, stats::setGoalkeeperSaves, "goalkeeperSaves", "saves");
        setIntegerIfPresent(items, homeSide, stats::setBigChancesCreated, "bigChancesCreated");
        setIntegerIfPresent(items, homeSide, stats::setBigChancesMissed, "bigChancesMissed");
        stats.setStatisticsProvider(Provider.SOFA_SCORE.name());
        stats.setStatisticsSyncedAt(Instant.now());
        return stats;
    }

    private boolean hasAdvancedMetrics(MatchAdvancedStats stats) {
        return stats.getPpda() != null || stats.getPpdaAtt() != null || stats.getPpdaDef() != null
                || stats.getFieldTilt() != null || stats.getXgChain() != null || stats.getXgBuildup() != null
                || stats.getPossession() != null || stats.getDeepCompletions() != null
                || stats.getFinalThirdEntries() != null || stats.getShotsFromBox() != null
                || stats.getShotsOutsideBox() != null || stats.getSetPieceXg() != null
                || stats.getCounterAttackXg() != null || stats.getTotalShots() != null
                || stats.getShotsOnTarget() != null || stats.getShotsOffTarget() != null
                || stats.getBlockedShots() != null || stats.getCornerKicks() != null
                || stats.getFouls() != null || stats.getYellowCards() != null
                || stats.getRedCards() != null || stats.getOffsides() != null
                || stats.getTotalPasses() != null || stats.getAccuratePasses() != null
                || stats.getPassAccuracy() != null || stats.getGoalkeeperSaves() != null
                || stats.getBigChancesCreated() != null || stats.getBigChancesMissed() != null;
    }

    private void setDecimalIfPresent(List<SofaMatchStatisticsData.StatisticItemDTO> items,
                                     boolean homeSide,
                                     java.util.function.Consumer<BigDecimal> setter,
                                     String... aliases) {
        BigDecimal value = statisticValue(items, homeSide, aliases);
        if (value != null) {
            setter.accept(value);
        }
    }

    private void setIntegerIfPresent(List<SofaMatchStatisticsData.StatisticItemDTO> items,
                                     boolean homeSide,
                                     java.util.function.Consumer<Integer> setter,
                                     String... aliases) {
        BigDecimal value = statisticValue(items, homeSide, aliases);
        if (value != null && value.signum() >= 0) {
            setter.accept(value.setScale(0, RoundingMode.HALF_UP).intValue());
        }
    }

    private BigDecimal statisticValue(List<SofaMatchStatisticsData.StatisticItemDTO> items,
                                      boolean homeSide,
                                      String... aliases) {
        Set<String> normalizedAliases = java.util.Arrays.stream(aliases)
                .map(this::normalizeStatisticKey)
                .collect(Collectors.toSet());

        for (SofaMatchStatisticsData.StatisticItemDTO item : items) {
            String key = normalizeStatisticKey(item.getKey());
            String name = normalizeStatisticKey(item.getName());
            if (!normalizedAliases.contains(key) && !normalizedAliases.contains(name)) {
                continue;
            }

            BigDecimal typedValue = homeSide ? item.getHomeValue() : item.getAwayValue();
            if (typedValue != null && typedValue.signum() >= 0) {
                return typedValue;
            }

            BigDecimal parsedValue = parseNumericValue(homeSide ? item.getHome() : item.getAway());
            if (parsedValue != null && parsedValue.signum() >= 0) {
                return parsedValue;
            }
        }
        return null;
    }

    private String normalizeStatisticKey(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private BigDecimal parseNumericValue(String value) {
        if (value == null || value.isBlank() || "-".equals(value.trim())) {
            return null;
        }
        Matcher matcher = NUMERIC_VALUE_PATTERN.matcher(value);
        if (!matcher.find()) {
            return null;
        }
        try {
            return new BigDecimal(matcher.group().replace(',', '.'));
        } catch (NumberFormatException exception) {
            return null;
        }
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
