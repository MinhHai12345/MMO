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
import com.mmo.module.fb.team.repository.TeamExternalIdRepository;
import com.mmo.module.fb.team.entity.Team;
import com.mmo.module.fb.ingest.provider.model.sofa.SofaMatchStatisticsData;
import com.mmo.module.fb.ingest.provider.model.sofa.SofaMatchesData;
import com.mmo.module.fb.ingest.provider.model.sofa.SofaSeasonData;
import com.mmo.module.fb.ingest.provider.model.sofa.SofaStandingsData;
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
    private final TeamExternalIdRepository teamExternalIdRepository;
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

    /**
     * Bootstraps SofaScore tournament IDs onto the canonical league catalogue.
     * Existing leagues are matched by slug/name before a new canonical league is created.
     */
    public void syncLeagues() {
        executeSimpleStorePipeline(sofaCrawlerService::fetchLeagues, tournaments -> {
            if (CollectionUtils.isEmpty(tournaments)) {
                log.warn("SofaScore returned no tournaments; league bootstrap was skipped");
                return;
            }

            int linked = 0;
            int created = 0;
            for (SofaSeasonData.TournamentDTO ignored : Collections.<SofaSeasonData.TournamentDTO>emptyList()) {
                // Keeps the DTO types separate; the actual tournament DTO is handled below.
            }
            for (com.mmo.module.fb.ingest.provider.model.sofa.SofaUniqueTournamentsData.UniqueTournamentDTO dto : tournaments) {
                if (dto == null || dto.getId() == null || dto.getName() == null || dto.getName().isBlank()) {
                    continue;
                }
                String externalId = dto.getId().toString();
                Optional<LeagueExternalId> existingMapping =
                        leagueExternalIdRepository.findByProviderAndExternalId(Provider.SOFA_SCORE, externalId);
                if (existingMapping.isPresent()) {
                    continue;
                }

                String slug = dto.getSlug();
                if (slug == null || slug.isBlank()) {
                    slug = dto.getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                            .replaceAll("(^-|-$)", "");
                }
                final String leagueSlug = slug;
                League league = leagueRepository.findAll().stream()
                        .filter(candidate -> candidate.getSlug() != null
                                && candidate.getSlug().equalsIgnoreCase(leagueSlug))
                        .findFirst()
                        .orElseGet(() -> leagueRepository.findAll().stream()
                                .filter(candidate -> normalizeName(candidate.getName()).equals(normalizeName(dto.getName())))
                                .findFirst().orElse(null));

                if (league == null) {
                    league = League.builder()
                            .name(dto.getName())
                            .slug(leagueSlug)
                            .country(dto.getCategory() == null ? null : dto.getCategory().getName())
                            .logoUrl(null)
                            .popularityScore(BigDecimal.ZERO)
                            .isActive(true)
                            .build();
                    league = leagueRepository.save(league);
                    created++;
                }
                if (leagueExternalIdRepository.findByLeagueIdAndProvider(league.getId(), Provider.SOFA_SCORE).isEmpty()) {
                    leagueExternalIdRepository.save(LeagueExternalId.builder()
                            .league(league)
                            .provider(Provider.SOFA_SCORE)
                            .externalId(externalId)
                            .build());
                    linked++;
                }
            }
            log.info("SofaScore league bootstrap completed: {} mappings created, {} canonical leagues created",
                    linked, created);
        });
    }

    /**
     * Links SofaScore seasons to the canonical league seasons. Missing league mappings are
     * skipped instead of aborting the whole ingestion run.
     */
    public void storeSeasons() {
        List<LeagueExternalId> mappings = leagueExternalIdRepository.findAllByProvider(Provider.SOFA_SCORE);
        if (CollectionUtils.isEmpty(mappings)) {
            log.warn("No SofaScore league mappings found. Run syncLeagues before storeSeasons.");
            return;
        }

        List<League> mappedLeagues = mappings.stream()
                .map(LeagueExternalId::getLeague)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        executeStorePipeline(mappedLeagues, (league, page) -> {
            LeagueExternalId mapping = leagueExternalIdRepository
                    .findByLeagueIdAndProvider(league.getId(), Provider.SOFA_SCORE).orElse(null);
            return mapping == null ? Collections.emptyList()
                    : sofaCrawlerService.fetchSeasonByTournamentId(Long.valueOf(mapping.getExternalId()), page);
        }, (league, seasonDtos) -> {
            if (CollectionUtils.isEmpty(seasonDtos)) {
                log.debug("No SofaScore seasons returned for league={}", league.getName());
                return;
            }

            for (SofaSeasonData.SeasonDTO dto : seasonDtos) {
                if (dto == null || dto.getId() == null) continue;
                String externalId = dto.getId().toString();
                if (seasonExternalIdRepository.findByProviderAndExternalId(Provider.SOFA_SCORE, externalId).isPresent()) {
                    continue;
                }

                String year = resolveSeasonYear(dto);
                if (year == null || year.isBlank()) {
                    log.warn("Skipping SofaScore season with no usable year for league={}", league.getName());
                    continue;
                }

                Season season = seasonRepository.findByLeague_IdAndYear(league.getId(), year).orElse(null);
                if (season == null) {
                    season = Season.builder()
                            .league(league)
                            .year(year)
                            .isCurrent(dto.isCurrent())
                            .build();
                    season = seasonRepository.save(season);
                } else if (dto.isCurrent() && !season.isCurrent()) {
                    season.setCurrent(true);
                    season = seasonRepository.save(season);
                }

                if (seasonExternalIdRepository.findBySeasonIdAndProvider(season.getId(), Provider.SOFA_SCORE).isEmpty()) {
                    seasonExternalIdRepository.save(SeasonExternalId.builder()
                            .season(season)
                            .provider(Provider.SOFA_SCORE)
                            .externalId(externalId)
                            .build());
                }
            }
        });
    }

    /**
     * Seeds team external IDs from the season standings. Teams are matched by normalized
     * name and only created when no canonical team exists.
     */
    public void syncTeams() {
        List<SeasonExternalId> seasonMappings = seasonExternalIdRepository.findAll().stream()
                .filter(mapping -> mapping.getProvider() == Provider.SOFA_SCORE)
                .toList();
        if (CollectionUtils.isEmpty(seasonMappings)) {
            log.warn("No SofaScore season mappings found. Run storeSeasons before syncTeams.");
            return;
        }

        executeStorePipeline(seasonMappings, (seasonMapping, page) -> {
            Season season = seasonMapping.getSeason();
            LeagueExternalId leagueMapping = leagueExternalIdRepository
                    .findByLeagueIdAndProvider(season.getLeague().getId(), Provider.SOFA_SCORE).orElse(null);
            if (leagueMapping == null) return Collections.emptyList();
            return sofaCrawlerService.fetchStandingTotal(Long.valueOf(leagueMapping.getExternalId()),
                    Long.valueOf(seasonMapping.getExternalId()), page);
        }, (seasonMapping, groups) -> {
            if (CollectionUtils.isEmpty(groups)) return;
            for (SofaStandingsData.StandingGroupDTO group : groups) {
                if (group == null || CollectionUtils.isEmpty(group.getRows())) continue;
                for (SofaStandingsData.StandingRowDTO row : group.getRows()) {
                    SofaStandingsData.TeamDTO dto = row == null ? null : row.getTeam();
                    if (dto == null || dto.getId() == null || dto.getName() == null || dto.getName().isBlank()) continue;
                    String externalId = dto.getId().toString();
                    if (teamExternalIdRepository.findByProviderAndExternalId(Provider.SOFA_SCORE, externalId).isPresent()) continue;

                    Team team = teamRepository.findByName(dto.getName()).orElseGet(() ->
                            teamRepository.findAll().stream()
                                    .filter(candidate -> normalizeName(candidate.getName()).equals(normalizeName(dto.getName())))
                                    .findFirst().orElse(null));
                    if (team == null) {
                        String teamSlug = dto.getSlug();
                        if (teamSlug == null || teamSlug.isBlank()) {
                            teamSlug = dto.getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                                    .replaceAll("(^-|-$)", "");
                        }
                        team = teamRepository.save(Team.builder()
                                .name(dto.getName())
                                .shortName(dto.getShortName())
                                .slug(teamSlug)
                                .ranking(dto.getRanking())
                                .isActive(true)
                                .build());
                    }
                    if (teamExternalIdRepository.findByTeamIdAndProvider(team.getId(), Provider.SOFA_SCORE).isEmpty()) {
                        teamExternalIdRepository.save(TeamExternalId.builder()
                                .team(team)
                                .provider(Provider.SOFA_SCORE)
                                .externalId(externalId)
                                .build());
                    }
                }
            }
        });
    }

    /**
     * Imports fixtures by season and round, matching canonical teams through SofaScore IDs.
     * The external event mapping is the idempotency key for subsequent runs.
     */
    public void storeMatches() {
        List<SeasonExternalId> seasons = seasonExternalIdRepository.findAll().stream()
                .filter(mapping -> mapping.getProvider() == Provider.SOFA_SCORE)
                .toList();
        if (CollectionUtils.isEmpty(seasons)) {
            log.warn("No SofaScore season mappings found. Run storeSeasons before storeMatches.");
            return;
        }

        executeStorePipeline(seasons, (seasonMapping, page) -> {
            LeagueExternalId leagueMapping = leagueExternalIdRepository
                    .findByLeagueIdAndProvider(seasonMapping.getSeason().getLeague().getId(), Provider.SOFA_SCORE)
                    .orElse(null);
            if (leagueMapping == null) return Collections.emptyList();

            List<com.mmo.module.fb.ingest.provider.model.sofa.SofaMatchesData.SofaEventDTO> events = new ArrayList<>();
            int emptyRounds = 0;
            for (int round = 1; round <= 50; round++) {
                List<com.mmo.module.fb.ingest.provider.model.sofa.SofaMatchesData.SofaEventDTO> roundEvents =
                        sofaCrawlerService.fetchMatchesByRound(Long.valueOf(leagueMapping.getExternalId()),
                                Long.valueOf(seasonMapping.getExternalId()), round, page);
                if (CollectionUtils.isEmpty(roundEvents)) {
                    emptyRounds++;
                    if (round > 10 && emptyRounds >= 3) break;
                    continue;
                }
                emptyRounds = 0;
                events.addAll(roundEvents);
            }
            return events;
        }, (seasonMapping, events) -> {
            if (CollectionUtils.isEmpty(events)) return;
            int saved = 0;
            for (com.mmo.module.fb.ingest.provider.model.sofa.SofaMatchesData.SofaEventDTO event : events) {
                if (event == null || event.getId() == null || event.getStartTimestamp() == null
                        || event.getHomeTeam() == null || event.getAwayTeam() == null
                        || event.getHomeTeam().getId() == null || event.getAwayTeam().getId() == null) continue;

                String externalId = event.getId().toString();
                if (matchExternalIdRepository.findByProviderAndExternalId(Provider.SOFA_SCORE, externalId).isPresent()) {
                    continue;
                }
                Team home = teamExternalIdRepository.findByProviderAndExternalId(
                        Provider.SOFA_SCORE, event.getHomeTeam().getId().toString())
                        .map(TeamExternalId::getTeam).orElse(null);
                Team away = teamExternalIdRepository.findByProviderAndExternalId(
                        Provider.SOFA_SCORE, event.getAwayTeam().getId().toString())
                        .map(TeamExternalId::getTeam).orElse(null);
                if (home == null || away == null) {
                    log.debug("Skipping SofaScore event {} because a team mapping is missing", externalId);
                    continue;
                }

                Instant kickoff = Instant.ofEpochSecond(event.getStartTimestamp());
                List<Match> candidates = matchRepository.findFixtureCandidates(
                        seasonMapping.getSeason().getId(), home.getId(), away.getId(),
                        kickoff.minus(6, ChronoUnit.HOURS), kickoff.plus(6, ChronoUnit.HOURS));
                Match match = candidates.stream().findFirst().orElse(null);
                if (match == null) {
                    match = Match.builder()
                            .season(seasonMapping.getSeason())
                            .homeTeam(home)
                            .awayTeam(away)
                            .matchTime(kickoff)
                            .status(resolveMatchStatus(event.getStatus() == null ? null : event.getStatus().getType()))
                            .homeScore(event.getHomeScore() == null ? null : event.getHomeScore().getCurrent())
                            .awayScore(event.getAwayScore() == null ? null : event.getAwayScore().getCurrent())
                            .roundInfo(event.getRoundInfo() == null || event.getRoundInfo().getRound() == null
                                    ? null : "Round " + event.getRoundInfo().getRound())
                            .build();
                    match = matchRepository.save(match);
                } else {
                    if (event.getStatus() != null) match.setStatus(resolveMatchStatus(event.getStatus().getType()));
                    if (event.getHomeScore() != null) match.setHomeScore(event.getHomeScore().getCurrent());
                    if (event.getAwayScore() != null) match.setAwayScore(event.getAwayScore().getCurrent());
                    match = matchRepository.save(match);
                }
                matchExternalIdRepository.save(MatchExternalId.builder()
                        .match(match)
                        .provider(Provider.SOFA_SCORE)
                        .externalId(externalId)
                        .build());
                saved++;
            }
            log.info("SofaScore fixture import for season {}: {} new event mappings",
                    seasonMapping.getSeason().getYear(), saved);
        });
    }

    public void syncInitialDataIfRequired() {
        if (!leagueExternalIdRepository.findAllByProvider(Provider.SOFA_SCORE).isEmpty()) {
            log.info("SofaScore core mappings already exist; initial bootstrap is not required");
            return;
        }
        log.info("No SofaScore core mappings found. Starting first-time SofaScore bootstrap.");
        syncLeagues();
        storeSeasons();
        syncTeams();
        storeMatches();
        log.info("First-time SofaScore core bootstrap completed");
    }

    private String resolveSeasonYear(SofaSeasonData.SeasonDTO dto) {
        if (dto.getYear() != null && !dto.getYear().isBlank()) return dto.getYear().trim();
        if (dto.getName() == null || dto.getName().isBlank()) return null;
        Matcher matcher = Pattern.compile("(20\\d{2})").matcher(dto.getName());
        return matcher.find() ? matcher.group(1) : dto.getName().trim();
    }

    private MatchStatus resolveMatchStatus(String statusType) {
        if (statusType == null) return MatchStatus.SCHEDULED;
        return switch (statusType.toLowerCase(Locale.ROOT)) {
            case "finished", "ended" -> MatchStatus.FINISHED;
            case "inprogress", "live", "halftime" -> MatchStatus.LIVE;
            case "postponed" -> MatchStatus.POSTPONED;
            case "canceled", "cancelled" -> MatchStatus.CANCELLED;
            default -> MatchStatus.SCHEDULED;
        };
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
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
