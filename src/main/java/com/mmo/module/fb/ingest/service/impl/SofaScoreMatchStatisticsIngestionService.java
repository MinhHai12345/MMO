package com.mmo.module.fb.ingest.service.impl;

import com.mmo.module.fb.domain.match.enums.MatchEnrichmentStatus;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.DataSourcePolicy;
import com.mmo.module.fb.ingest.policy.Dataset;
import com.mmo.module.fb.ingest.persistence.MatchXgPersistenceService;
import com.mmo.module.fb.ingest.application.port.out.MatchStatisticsIngestionPort;
import com.mmo.module.fb.ingest.provider.model.ProviderMatchAdvancedStatistics;
import com.mmo.module.fb.ingest.provider.spi.MatchStatisticsProvider;
import com.mmo.module.fb.ingest.provider.spi.ProviderRegistry;
import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.match.entity.MatchAdvancedStats;
import com.mmo.module.fb.domain.match.entity.MatchExternalId;
import com.mmo.module.fb.domain.match.enums.MatchStatus;
import com.mmo.module.fb.domain.match.repository.MatchAdvancedStatsRepository;
import com.mmo.module.fb.domain.match.repository.MatchExternalIdRepository;
import com.mmo.module.fb.domain.match.repository.MatchRepository;
import com.mmo.module.fb.domain.match.enums.MatchEnrichmentResource;
import com.mmo.module.fb.domain.match.enums.MatchProvider;
import com.mmo.module.fb.domain.match.service.MatchEnrichmentStateService;
import com.mmo.module.fb.domain.match.service.MatchStatisticsSnapshotPersistenceService;
import com.mmo.module.fb.domain.team.entity.Team;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Application ingestion service. It resolves canonical matches and persists provider-neutral
 * statistics; provider DTOs and browser transport remain inside the SofaScore adapter.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SofaScoreMatchStatisticsIngestionService implements MatchStatisticsIngestionPort {

    private static final int BATCH_SIZE = 30;
    private static final int RETRY_HOURS = 24;

    private final ProviderRegistry providerRegistry;
    private final MatchRepository matchRepository;
    private final MatchExternalIdRepository matchExternalIdRepository;
    private final MatchAdvancedStatsRepository matchAdvancedStatsRepository;
    private final MatchStatisticsSnapshotPersistenceService snapshotPersistenceService;
    private final MatchEnrichmentStateService enrichmentStateService;
    private final DataSourcePolicy dataSourcePolicy;
    private final MatchXgPersistenceService matchXgPersistenceService;

    public void storeMatchStatistics() {
        List<Match> targetMatches = matchRepository.findFinishedMatchesMissingAdvancedStats(
                MatchStatus.FINISHED, MatchProvider.SOFA_SCORE, MatchEnrichmentResource.TEAM_STATISTICS,
                MatchEnrichmentStatus.SUCCEEDED,
                MatchEnrichmentStatus.IN_PROGRESS,
                Instant.now().minus(RETRY_HOURS, ChronoUnit.HOURS), PageRequest.of(0, BATCH_SIZE));

        if (targetMatches == null || targetMatches.isEmpty()) {
            log.debug("No finished matches are missing SofaScore advanced statistics");
            return;
        }

        MatchStatisticsProvider provider = providerRegistry.matchStatistics(Provider.SOFA_SCORE);
        int persisted = 0;
        int skipped = 0;
        int failed = 0;
        for (Match match : targetMatches) {
            enrichmentStateService.start(match, MatchProvider.SOFA_SCORE, MatchEnrichmentResource.TEAM_STATISTICS);
            try {
                Optional<MatchExternalId> mapping = matchExternalIdRepository
                        .findByMatchIdAndProvider(match.getId(), MatchProvider.SOFA_SCORE);
                if (mapping.isEmpty()) {
                    enrichmentStateService.skipped(match, MatchProvider.SOFA_SCORE, MatchEnrichmentResource.TEAM_STATISTICS);
                    skipped++;
                    continue;
                }

                Optional<ProviderMatchAdvancedStatistics> snapshot =
                        provider.fetchMatchStatistics(mapping.get().getExternalId());
                if (snapshot.isEmpty()) {
                        enrichmentStateService.empty(match, MatchProvider.SOFA_SCORE, MatchEnrichmentResource.TEAM_STATISTICS);
                    skipped++;
                    continue;
                }

                ProviderMatchAdvancedStatistics providerSnapshot = snapshot.get();
                if (!hasStatistics(providerSnapshot.home()) && !hasStatistics(providerSnapshot.away())) {
                    enrichmentStateService.empty(match, MatchProvider.SOFA_SCORE, MatchEnrichmentResource.TEAM_STATISTICS);
                    skipped++;
                    continue;
                }

                persistMatchStatistics(match, providerSnapshot);
                enrichmentStateService.succeed(match, MatchProvider.SOFA_SCORE, MatchEnrichmentResource.TEAM_STATISTICS);
                persisted++;
            } catch (RuntimeException exception) {
                enrichmentStateService.failed(match, MatchProvider.SOFA_SCORE, MatchEnrichmentResource.TEAM_STATISTICS, exception);
                failed++;
                log.warn("SofaScore statistics sync failed for matchId={}: {}",
                        match.getId(), exception.getMessage(), exception);
            }
        }

        log.info("SofaScore match-statistics sync finished. candidates={}, persisted={}, skipped={}, failed={}",
                targetMatches.size(), persisted, skipped, failed);
        if (failed > 0) {
            throw new IllegalStateException("SofaScore statistics sync failed for " + failed + " match(es)");
        }
    }

    private void persistMatchStatistics(Match match, ProviderMatchAdvancedStatistics snapshot) {
        if (match.getSeason() == null
                || !dataSourcePolicy.isAllowed(Provider.SOFA_SCORE, Dataset.MATCH_STATISTICS, match.getSeason())) {
            return;
        }
        Instant now = Instant.now();

        BigDecimal homeXg = statisticValue(snapshot.home(), "expectedGoals", "xg");
        BigDecimal awayXg = statisticValue(snapshot.away(), "expectedGoals", "xg");
        matchXgPersistenceService.upsert(Provider.SOFA_SCORE, match, homeXg, awayXg);

        MatchAdvancedStats homeStats = upsertTeamStatistics(
                match, match.getHomeTeam(), snapshot.home(), now);
        MatchAdvancedStats awayStats = upsertTeamStatistics(
                match, match.getAwayTeam(), snapshot.away(), now);

        // Persist a snapshot when xG exists even if the provider does not expose other metrics.
        boolean hasMatchXg = match.getHomeXG() != null || match.getAwayXG() != null;
        List<MatchAdvancedStats> statsToSave = new ArrayList<>(2);
        if (homeStats != null && (hasAdvancedMetrics(homeStats) || hasMatchXg)) statsToSave.add(homeStats);
        if (awayStats != null && (hasAdvancedMetrics(awayStats) || hasMatchXg)) statsToSave.add(awayStats);
        if (!statsToSave.isEmpty()) matchAdvancedStatsRepository.saveAll(statsToSave);
    }

    private MatchAdvancedStats upsertTeamStatistics(
            Match match, Team team, Map<String, BigDecimal> values, Instant syncedAt) {
        if (team == null) return null;

        snapshotPersistenceService.upsert(match, team, MatchProvider.SOFA_SCORE, values);

        MatchAdvancedStats stats = matchAdvancedStatsRepository
                .findByMatchIdAndTeamId(match.getId(), team.getId())
                .orElseGet(() -> MatchAdvancedStats.builder().match(match).team(team).build());

        setDecimalIfPresent(values, stats.getPpda(), stats::setPpda,
                "ppda", "opponentHalfPressuresPerDefensiveAction");
        setIntegerIfPresent(values, stats.getPpdaAtt(), stats::setPpdaAtt, "ppdaAtt");
        setIntegerIfPresent(values, stats.getPpdaDef(), stats::setPpdaDef, "ppdaDef");
        setDecimalIfPresent(values, stats.getFieldTilt(), stats::setFieldTilt, "fieldTilt");
        setDecimalIfPresent(values, stats.getXgChain(), stats::setXgChain, "xgChain");
        setDecimalIfPresent(values, stats.getXgBuildup(), stats::setXgBuildup, "xgBuildup");
        setDecimalIfPresent(values, stats.getPossession(), stats::setPossession, "ballPossession", "possession");
        setIntegerIfPresent(values, stats.getDeepCompletions(), stats::setDeepCompletions, "deepCompletions");
        setIntegerIfPresent(values, stats.getFinalThirdEntries(), stats::setFinalThirdEntries, "finalThirdEntries");
        setIntegerIfPresent(values, stats.getShotsFromBox(), stats::setShotsFromBox,
                "shotsInsideBox", "shotsFromInsideTheBox", "shotsFromBox");
        setIntegerIfPresent(values, stats.getShotsOutsideBox(), stats::setShotsOutsideBox,
                "shotsOutsideBox", "shotsFromOutsideTheBox");
        setDecimalIfPresent(values, stats.getSetPieceXg(), stats::setSetPieceXg,
                "setPieceXg", "setPieceExpectedGoals");
        setDecimalIfPresent(values, stats.getCounterAttackXg(), stats::setCounterAttackXg,
                "counterAttackXg", "counterAttackExpectedGoals");
        setIntegerIfPresent(values, stats.getTotalShots(), stats::setTotalShots,
                "totalShots", "shotsTotal", "totalShotsAttempted");
        setIntegerIfPresent(values, stats.getShotsOnTarget(), stats::setShotsOnTarget,
                "shotsOnTarget", "shotsOnGoal", "shotsOnTargetTotal");
        setIntegerIfPresent(values, stats.getShotsOffTarget(), stats::setShotsOffTarget,
                "shotsOffTarget", "shotsOffGoal", "shotsOffTargetTotal");
        setIntegerIfPresent(values, stats.getBlockedShots(), stats::setBlockedShots, "blockedShots", "blockedShotsTotal");
        setIntegerIfPresent(values, stats.getCornerKicks(), stats::setCornerKicks, "cornerKicks", "corners", "cornerKicksTotal");
        setIntegerIfPresent(values, stats.getFouls(), stats::setFouls, "fouls", "totalFouls");
        setIntegerIfPresent(values, stats.getYellowCards(), stats::setYellowCards, "yellowCards", "yellowCardsTotal");
        setIntegerIfPresent(values, stats.getRedCards(), stats::setRedCards, "redCards", "redCardsTotal");
        setIntegerIfPresent(values, stats.getOffsides(), stats::setOffsides, "offsides", "offside");
        setIntegerIfPresent(values, stats.getTotalPasses(), stats::setTotalPasses, "totalPasses", "passes");
        setIntegerIfPresent(values, stats.getAccuratePasses(), stats::setAccuratePasses, "accuratePasses", "passesAccurate");
        setDecimalIfPresent(values, stats.getPassAccuracy(), stats::setPassAccuracy,
                "accuratePassesPercentage", "passAccuracy", "passesAccuracy");
        setIntegerIfPresent(values, stats.getGoalkeeperSaves(), stats::setGoalkeeperSaves, "goalkeeperSaves", "saves");
        setIntegerIfPresent(values, stats.getBigChancesCreated(), stats::setBigChancesCreated, "bigChancesCreated");
        setIntegerIfPresent(values, stats.getBigChancesMissed(), stats::setBigChancesMissed, "bigChancesMissed");
        // The source-specific payload and fetched timestamp are stored in the snapshot table.
        return stats;
    }

    private boolean hasStatistics(Map<String, BigDecimal> values) {
        return values != null && !values.isEmpty();
    }

    private boolean hasAdvancedMetrics(MatchAdvancedStats stats) {
        if (stats == null) return false;
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

    private void setDecimalIfPresent(
            Map<String, BigDecimal> values, BigDecimal current,
            Consumer<BigDecimal> setter, String... aliases) {
        if (current != null) return;
        BigDecimal value = statisticValue(values, aliases);
        if (value != null) setter.accept(value);
    }

    private void setIntegerIfPresent(
            Map<String, BigDecimal> values, Integer current,
            Consumer<Integer> setter, String... aliases) {
        if (current != null) return;
        BigDecimal value = statisticValue(values, aliases);
        if (value != null && value.signum() >= 0) {
            setter.accept(value.setScale(0, RoundingMode.HALF_UP).intValue());
        }
    }

    private BigDecimal statisticValue(Map<String, BigDecimal> values, String... aliases) {
        if (values == null || values.isEmpty()) return null;
        for (String alias : aliases) {
            BigDecimal value = values.get(normalizeStatisticKey(alias));
            if (value != null && value.signum() >= 0) return value;
        }
        return null;
    }

    private String normalizeStatisticKey(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
