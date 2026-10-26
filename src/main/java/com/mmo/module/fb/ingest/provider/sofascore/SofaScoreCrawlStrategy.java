package com.mmo.module.fb.ingest.provider.sofascore;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.sofascore.client.AbstractSofaScoreProvider;
import com.mmo.module.fb.ingest.provider.sofascore.client.SofaScoreClient;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaScoreResponseDTO.GroupDTO;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaScoreResponseDTO.PeriodStatisticsDTO;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaScoreResponseDTO.StatisticItemDTO;
import com.mmo.module.fb.match.entity.Match;
import com.mmo.module.fb.match.entity.MatchAdvancedStats;
import com.mmo.module.fb.match.entity.MatchExternalId;
import com.mmo.module.fb.match.enums.MatchStatus;
import com.mmo.module.fb.match.repository.MatchAdvancedStatsRepository;
import com.mmo.module.fb.match.repository.MatchExternalIdRepository;
import com.mmo.module.fb.match.repository.MatchRepository;
import com.mmo.module.fb.team.entity.Team;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * SofaScore-only ingestion of event statistics. Core football entities are imported through
 * the shared provider SPI and persistence services; this class only enriches canonical matches
 * with xG and per-team advanced statistics.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SofaScoreCrawlStrategy extends AbstractSofaScoreProvider {

    private static final Pattern NUMERIC_VALUE_PATTERN = Pattern.compile("-?\\d+(?:[.,]\\d+)?");

    private final MatchRepository matchRepository;
    private final MatchExternalIdRepository matchExternalIdRepository;
    private final MatchAdvancedStatsRepository matchAdvancedStatsRepository;
    private final SofaScoreClient sofaScoreClient;

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
                        List<PeriodStatisticsDTO> statistics =
                                sofaScoreClient.fetchMatchStatistics(
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
                                        List<PeriodStatisticsDTO> periods) {
        if (match == null) {
            return;
        }
        match.setStatisticsLastAttemptAt(Instant.now());
        matchRepository.save(match);
        if (CollectionUtils.isEmpty(periods)) {
            return;
        }

        List<StatisticItemDTO> items = periods.stream()
                .filter(Objects::nonNull)
                .filter(period -> "ALL".equalsIgnoreCase(period.period()))
                .findFirst()
                .map(PeriodStatisticsDTO::groups)
                .orElseGet(() -> periods.stream()
                        .filter(Objects::nonNull)
                        .filter(period -> CollectionUtils.isNotEmpty(period.groups()))
                        .findFirst()
                        .map(PeriodStatisticsDTO::groups)
                        .orElse(Collections.emptyList()))
                .stream()
                .filter(Objects::nonNull)
                .map(GroupDTO::statisticsItems)
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

    private MatchAdvancedStats upsertTeamStatistics(Match match, Team team,
                                                    List<StatisticItemDTO> items,
                                                    boolean homeSide) {
        MatchAdvancedStats stats = matchAdvancedStatsRepository
                .findByMatchIdAndTeamId(match.getId(), team.getId())
                .orElseGet(() -> MatchAdvancedStats.builder().match(match).team(team).build());

        setDecimalIfPresent(items, homeSide, stats::setPpda,
                "ppda", "opponentHalfPressuresPerDefensiveAction");
        setIntegerIfPresent(items, homeSide, stats::setPpdaAtt, "ppdaAtt");
        setIntegerIfPresent(items, homeSide, stats::setPpdaDef, "ppdaDef");
        setDecimalIfPresent(items, homeSide, stats::setFieldTilt, "fieldTilt");
        setDecimalIfPresent(items, homeSide, stats::setXgChain, "xgChain");
        setDecimalIfPresent(items, homeSide, stats::setXgBuildup, "xgBuildup");
        setDecimalIfPresent(items, homeSide, stats::setPossession, "ballPossession", "possession");
        setIntegerIfPresent(items, homeSide, stats::setDeepCompletions, "deepCompletions");
        setIntegerIfPresent(items, homeSide, stats::setFinalThirdEntries, "finalThirdEntries");
        setIntegerIfPresent(items, homeSide, stats::setShotsFromBox,
                "shotsInsideBox", "shotsFromInsideTheBox", "shotsFromBox");
        setIntegerIfPresent(items, homeSide, stats::setShotsOutsideBox,
                "shotsOutsideBox", "shotsFromOutsideTheBox");
        setDecimalIfPresent(items, homeSide, stats::setSetPieceXg,
                "setPieceXg", "setPieceExpectedGoals");
        setDecimalIfPresent(items, homeSide, stats::setCounterAttackXg,
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

    private void setDecimalIfPresent(List<StatisticItemDTO> items,
                                     boolean homeSide,
                                     java.util.function.Consumer<BigDecimal> setter,
                                     String... aliases) {
        BigDecimal value = statisticValue(items, homeSide, aliases);
        if (value != null) {
            setter.accept(value);
        }
    }

    private void setIntegerIfPresent(List<StatisticItemDTO> items,
                                     boolean homeSide,
                                     java.util.function.Consumer<Integer> setter,
                                     String... aliases) {
        BigDecimal value = statisticValue(items, homeSide, aliases);
        if (value != null && value.signum() >= 0) {
            setter.accept(value.setScale(0, RoundingMode.HALF_UP).intValue());
        }
    }

    private BigDecimal statisticValue(List<StatisticItemDTO> items,
                                      boolean homeSide,
                                      String... aliases) {
        Set<String> normalizedAliases = java.util.Arrays.stream(aliases)
                .map(this::normalizeStatisticKey)
                .collect(Collectors.toSet());

        for (StatisticItemDTO item : items) {
            String key = normalizeStatisticKey(item.key());
            String name = normalizeStatisticKey(item.name());
            if (!normalizedAliases.contains(key) && !normalizedAliases.contains(name)) {
                continue;
            }

            BigDecimal typedValue = homeSide ? item.homeValue() : item.awayValue();
            if (typedValue != null && typedValue.signum() >= 0) {
                return typedValue;
            }

            BigDecimal parsedValue = parseNumericValue(homeSide ? item.home() : item.away());
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

    public Provider getProvider() {
        return Provider.SOFA_SCORE;
    }
}
