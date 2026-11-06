package com.mmo.module.fb.ingest.application.service.integration;

import com.mmo.module.fb.domain.match.enums.MatchEnrichmentStatus;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.DataSourcePolicy;
import com.mmo.module.fb.ingest.policy.Dataset;
import com.mmo.module.fb.ingest.application.port.out.MatchXgPersistencePort;
import com.mmo.module.fb.ingest.application.port.out.SofaScoreMatchEnrichmentQueryPort;
import com.mmo.module.fb.ingest.application.port.out.SourceAwareMergePort;
import com.mmo.module.fb.ingest.application.port.out.MatchStatisticsIngestionPort;
import com.mmo.module.fb.ingest.provider.model.ProviderMatchAdvancedStatistics;
import com.mmo.module.fb.ingest.application.port.out.provider.MatchStatisticsIngestionProviderPort;
import com.mmo.module.fb.ingest.application.registry.ProviderIngestionRegistry;
import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.match.entity.MatchAdvancedStats;
import com.mmo.module.fb.domain.match.enums.MatchEnrichmentResource;
import com.mmo.module.fb.domain.match.enums.MatchProvider;
import com.mmo.module.fb.domain.match.service.MatchEnrichmentStateService;
import com.mmo.module.fb.domain.match.service.MatchStatisticsSnapshotPersistenceService;
import com.mmo.module.fb.domain.team.entity.Team;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final ProviderIngestionRegistry providerRegistry;
    private final SofaScoreMatchEnrichmentQueryPort matchEnrichmentQueryPort;
    private final MatchStatisticsSnapshotPersistenceService snapshotPersistenceService;
    private final MatchEnrichmentStateService enrichmentStateService;
    private final DataSourcePolicy dataSourcePolicy;
    private final MatchXgPersistencePort matchXgPersistenceService;
    private final SourceAwareMergePort sourceAwareMergeService;

    public void storeMatchStatistics() {
        List<Match> targetMatches = matchEnrichmentQueryPort.findMatchesMissingAdvancedStatistics(
                Instant.now().minus(RETRY_HOURS, ChronoUnit.HOURS), BATCH_SIZE);

        if (targetMatches == null || targetMatches.isEmpty()) {
            log.debug("No finished matches are missing SofaScore advanced statistics");
            return;
        }

        MatchStatisticsIngestionProviderPort provider = providerRegistry.matchStatistics(Provider.SOFA_SCORE);
        int persisted = 0;
        int skipped = 0;
        int failed = 0;
        for (Match match : targetMatches) {
            enrichmentStateService.start(match, MatchProvider.SOFA_SCORE, MatchEnrichmentResource.TEAM_STATISTICS);
            try {
                Optional<String> providerMatchId = matchEnrichmentQueryPort
                        .findProviderMatchId(match.getId(), MatchProvider.SOFA_SCORE);
                if (providerMatchId.isEmpty()) {
                    enrichmentStateService.skipped(match, MatchProvider.SOFA_SCORE, MatchEnrichmentResource.TEAM_STATISTICS);
                    skipped++;
                    continue;
                }

                Optional<ProviderMatchAdvancedStatistics> snapshot =
                        provider.fetchMatchStatistics(providerMatchId.get());
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
        if (!statsToSave.isEmpty()) matchEnrichmentQueryPort.saveAdvancedStatistics(statsToSave);
    }

    private MatchAdvancedStats upsertTeamStatistics(
            Match match, Team team, Map<String, BigDecimal> values, Instant syncedAt) {
        if (team == null) return null;

        snapshotPersistenceService.upsert(match, team, MatchProvider.SOFA_SCORE, values);

        MatchAdvancedStats stats = matchEnrichmentQueryPort
                .findTeamAdvancedStats(match.getId(), team.getId())
                .orElseGet(() -> MatchAdvancedStats.builder().match(match).team(team).build());

        setDecimalIfPresent(match, team, "ppda", values, stats.getPpda(), stats::setPpda,
                "ppda", "opponentHalfPressuresPerDefensiveAction");
        setIntegerIfPresent(match, team, "ppda_att", values, stats.getPpdaAtt(), stats::setPpdaAtt, "ppdaAtt");
        setIntegerIfPresent(match, team, "ppda_def", values, stats.getPpdaDef(), stats::setPpdaDef, "ppdaDef");
        setDecimalIfPresent(match, team, "field_tilt", values, stats.getFieldTilt(), stats::setFieldTilt, "fieldTilt");
        setDecimalIfPresent(match, team, "xg_chain", values, stats.getXgChain(), stats::setXgChain, "xgChain");
        setDecimalIfPresent(match, team, "xg_buildup", values, stats.getXgBuildup(), stats::setXgBuildup, "xgBuildup");
        setDecimalIfPresent(match, team, "possession", values, stats.getPossession(), stats::setPossession, "ballPossession", "possession");
        setIntegerIfPresent(match, team, "deep_completions", values, stats.getDeepCompletions(), stats::setDeepCompletions, "deepCompletions");
        setIntegerIfPresent(match, team, "final_third_entries", values, stats.getFinalThirdEntries(), stats::setFinalThirdEntries, "finalThirdEntries");
        setIntegerIfPresent(match, team, "shots_from_box", values, stats.getShotsFromBox(), stats::setShotsFromBox,
                "shotsInsideBox", "shotsFromInsideTheBox", "shotsFromBox");
        setIntegerIfPresent(match, team, "shots_outside_box", values, stats.getShotsOutsideBox(), stats::setShotsOutsideBox,
                "shotsOutsideBox", "shotsFromOutsideTheBox");
        setDecimalIfPresent(match, team, "set_piece_xg", values, stats.getSetPieceXg(), stats::setSetPieceXg,
                "setPieceXg", "setPieceExpectedGoals");
        setDecimalIfPresent(match, team, "counter_attack_xg", values, stats.getCounterAttackXg(), stats::setCounterAttackXg,
                "counterAttackXg", "counterAttackExpectedGoals");
        setIntegerIfPresent(match, team, "total_shots", values, stats.getTotalShots(), stats::setTotalShots,
                "totalShots", "shotsTotal", "totalShotsAttempted");
        setIntegerIfPresent(match, team, "shots_on_target", values, stats.getShotsOnTarget(), stats::setShotsOnTarget,
                "shotsOnTarget", "shotsOnGoal", "shotsOnTargetTotal");
        setIntegerIfPresent(match, team, "shots_off_target", values, stats.getShotsOffTarget(), stats::setShotsOffTarget,
                "shotsOffTarget", "shotsOffGoal", "shotsOffTargetTotal");
        setIntegerIfPresent(match, team, "blocked_shots", values, stats.getBlockedShots(), stats::setBlockedShots, "blockedShots", "blockedShotsTotal");
        setIntegerIfPresent(match, team, "corner_kicks", values, stats.getCornerKicks(), stats::setCornerKicks, "cornerKicks", "corners", "cornerKicksTotal");
        setIntegerIfPresent(match, team, "fouls", values, stats.getFouls(), stats::setFouls, "fouls", "totalFouls");
        setIntegerIfPresent(match, team, "yellow_cards", values, stats.getYellowCards(), stats::setYellowCards, "yellowCards", "yellowCardsTotal");
        setIntegerIfPresent(match, team, "red_cards", values, stats.getRedCards(), stats::setRedCards, "redCards", "redCardsTotal");
        setIntegerIfPresent(match, team, "offsides", values, stats.getOffsides(), stats::setOffsides, "offsides", "offside");
        setIntegerIfPresent(match, team, "total_passes", values, stats.getTotalPasses(), stats::setTotalPasses, "totalPasses", "passes");
        setIntegerIfPresent(match, team, "accurate_passes", values, stats.getAccuratePasses(), stats::setAccuratePasses, "accuratePasses", "passesAccurate");
        setDecimalIfPresent(match, team, "pass_accuracy", values, stats.getPassAccuracy(), stats::setPassAccuracy,
                "accuratePassesPercentage", "passAccuracy", "passesAccuracy");
        setIntegerIfPresent(match, team, "goalkeeper_saves", values, stats.getGoalkeeperSaves(), stats::setGoalkeeperSaves, "goalkeeperSaves", "saves");
        setIntegerIfPresent(match, team, "big_chances_created", values, stats.getBigChancesCreated(), stats::setBigChancesCreated, "bigChancesCreated");
        setIntegerIfPresent(match, team, "big_chances_missed", values, stats.getBigChancesMissed(), stats::setBigChancesMissed, "bigChancesMissed");
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
            Match match, Team team, String field, Map<String, BigDecimal> values, BigDecimal current,
            Consumer<BigDecimal> setter, String... aliases) {
        BigDecimal value = statisticValue(values, aliases);
        if (value == null) return;
        sourceAwareMergeService.merge(match, Provider.SOFA_SCORE, Dataset.MATCH_STATISTICS,
                "team:" + team.getId() + ":" + field, current != null, null, () -> setter.accept(value));
    }

    private void setIntegerIfPresent(
            Match match, Team team, String field, Map<String, BigDecimal> values, Integer current,
            Consumer<Integer> setter, String... aliases) {
        BigDecimal value = statisticValue(values, aliases);
        if (value != null && value.signum() >= 0) {
            int normalized = value.setScale(0, RoundingMode.HALF_UP).intValue();
            sourceAwareMergeService.merge(match, Provider.SOFA_SCORE, Dataset.MATCH_STATISTICS,
                    "team:" + team.getId() + ":" + field, current != null, null, () -> setter.accept(normalized));
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
