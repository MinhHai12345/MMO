package com.mmo.module.fb.ingest.adapter.out.persistence.provider.footballdatauk;

import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.match.entity.MatchAdvancedStats;
import com.mmo.module.fb.domain.match.enums.MatchProvider;
import com.mmo.module.fb.domain.match.repository.MatchAdvancedStatsRepository;
import com.mmo.module.fb.domain.match.service.MatchStatisticsSnapshotPersistenceService;
import com.mmo.module.fb.domain.team.entity.Team;
import com.mmo.module.fb.ingest.adapter.out.persistence.core.SourceAwareMergeAdapter;
import com.mmo.module.fb.ingest.application.model.FieldMergeCommand;
import com.mmo.module.fb.ingest.application.port.out.FootballDataUkMatchStatisticsPersistencePort;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.DataSourcePolicy;
import com.mmo.module.fb.ingest.policy.Dataset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Batch persistence for Football-Data UK statistics and source snapshots. */
@Repository
@RequiredArgsConstructor
public class FootballDataUkMatchStatisticsPersistenceAdapter
        implements FootballDataUkMatchStatisticsPersistencePort {

    private final MatchAdvancedStatsRepository statsRepository;
    private final MatchStatisticsSnapshotPersistenceService snapshotPersistenceService;
    private final DataSourcePolicy dataSourcePolicy;
    private final SourceAwareMergeAdapter sourceAwareMergeService;

    @Override
    @Transactional
    public int persist(Match match, String seasonYear, Map<String, String> row) {
        return persistAll(List.of(new StatisticsInput(match, seasonYear, row)));
    }

    /**
     * Processes all fixture statistics in one source-aware merge. This batches the
     * season/league prefetch, match locks and provenance read/write across the chunk.
     */
    @Override
    @Transactional
    public int persistAll(List<StatisticsInput> inputs) {
        if (inputs == null || inputs.isEmpty()) return 0;
        List<StatisticsInput> valid = inputs.stream()
                .filter(input -> input != null && input.match() != null && input.match().getId() != null
                        && StringUtils.hasText(input.seasonYear()) && input.row() != null
                        && dataSourcePolicy.isPrimary(
                                Provider.FOOTBALL_DATA_UK, Dataset.MATCH_STATISTICS, input.seasonYear()))
                .toList();
        if (valid.isEmpty()) return 0;

        List<Long> matchIds = valid.stream().map(input -> input.match().getId()).distinct().toList();
        Map<StatsKey, MatchAdvancedStats> statsByKey = new HashMap<>();
        statsRepository.findAllByMatchIdIn(matchIds).forEach(stats ->
                statsByKey.put(new StatsKey(stats.getMatch().getId(), stats.getTeam().getId()), stats));

        List<FieldMergeCommand> commands = new ArrayList<>(valid.size() * 30);
        Map<StatsKey, MatchAdvancedStats> changedStats = new LinkedHashMap<>();
        List<MatchStatisticsSnapshotPersistenceService.SnapshotInput> snapshots = new ArrayList<>(valid.size() * 2);
        for (StatisticsInput input : valid) {
            collectSide(input.match(), input.row(), input.match().getHomeTeam(), "H",
                    statsByKey, changedStats, commands, snapshots);
            collectSide(input.match(), input.row(), input.match().getAwayTeam(), "A",
                    statsByKey, changedStats, commands, snapshots);
        }
        if (commands.isEmpty()) return 0;

        // Keep raw provider payloads even when a particular canonical field is rejected
        // by the provider-priority/provenance gate.
        snapshotPersistenceService.upsertAll(snapshots);
        int applied = sourceAwareMergeService.mergeAll(commands);
        if (applied == 0) return 0;
        statsRepository.saveAll(changedStats.values());
        return changedStats.size();
    }

    private void collectSide(Match match, Map<String, String> row, Team team, String side,
                             Map<StatsKey, MatchAdvancedStats> statsByKey,
                             Map<StatsKey, MatchAdvancedStats> changedStats,
                             List<FieldMergeCommand> commands,
                             List<MatchStatisticsSnapshotPersistenceService.SnapshotInput> snapshots) {
        Integer shots = integer(value(row, side + "S"));
        Integer onTarget = integer(value(row, side + "ST"));
        Integer hitWoodwork = integer(value(row, side + "HW"));
        Integer corners = integer(value(row, side + "C"));
        Integer fouls = integer(value(row, side + "F"));
        Integer freeKicksConceded = integer(value(row, side + "FKC"));
        Integer offsides = integer(value(row, side + "O"));
        Integer yellow = integer(value(row, side + "Y"));
        Integer red = integer(value(row, side + "R"));
        Integer bookingPoints = integer(value(row, side + "BP"));

        Integer homeGoals = integer(valueAny(row, "FTHG", "HG"));
        Integer awayGoals = integer(valueAny(row, "FTAG", "AG"));
        Integer scored = homeGoals == null || awayGoals == null ? null
                : (side.equals("H") ? homeGoals : awayGoals);
        String outcome = homeGoals == null || awayGoals == null ? null
                : homeGoals.equals(awayGoals) ? "D"
                : (side.equals("H") == (homeGoals > awayGoals) ? "W" : "L");

        BigDecimal xg = decimalMetric(value(row, side.equals("H") ? "HXG" : "AXG"));
        BigDecimal xga = decimalMetric(value(row, side.equals("H") ? "AXG" : "HXG"));
        if (shots == null && onTarget == null && hitWoodwork == null && corners == null
                && fouls == null && freeKicksConceded == null && offsides == null
                && yellow == null && red == null && bookingPoints == null && xg == null && scored == null) {
            return;
        }

        snapshots.add(new MatchStatisticsSnapshotPersistenceService.SnapshotInput(
                match, team, MatchProvider.FOOTBALL_DATA_UK, row));
        StatsKey key = new StatsKey(match.getId(), team.getId());
        MatchAdvancedStats stats = statsByKey.computeIfAbsent(key, ignored ->
                MatchAdvancedStats.builder().match(match).team(team).build());
        changedStats.put(key, stats);

        addMerge(commands, match, team, "total_shots", stats.getTotalShots(), shots, stats::setTotalShots);
        addMerge(commands, match, team, "shots_on_target", stats.getShotsOnTarget(), onTarget, stats::setShotsOnTarget);
        addMerge(commands, match, team, "hit_woodwork", stats.getHitWoodwork(), hitWoodwork, stats::setHitWoodwork);
        addMerge(commands, match, team, "corner_kicks", stats.getCornerKicks(), corners, stats::setCornerKicks);
        addMerge(commands, match, team, "fouls", stats.getFouls(), fouls, stats::setFouls);
        addMerge(commands, match, team, "free_kicks_conceded", stats.getFreeKicksConceded(), freeKicksConceded, stats::setFreeKicksConceded);
        addMerge(commands, match, team, "offsides", stats.getOffsides(), offsides, stats::setOffsides);
        addMerge(commands, match, team, "yellow_cards", stats.getYellowCards(), yellow, stats::setYellowCards);
        addMerge(commands, match, team, "red_cards", stats.getRedCards(), red, stats::setRedCards);
        addMerge(commands, match, team, "booking_points", stats.getBookingPoints(), bookingPoints, stats::setBookingPoints);
        addMerge(commands, match, team, "xg", stats.getXg(), xg, stats::setXg);
        addMerge(commands, match, team, "xga_against", stats.getXga(), xga, stats::setXga);
        addMerge(commands, match, team, "scored", stats.getScored(), scored, stats::setScored);
        if (outcome != null) {
            addMerge(commands, match, team, "result", stats.getResult(), outcome, stats::setResult);
            addMerge(commands, match, team, "wins", stats.getWins(), "W".equals(outcome) ? 1 : 0, stats::setWins);
            addMerge(commands, match, team, "draws", stats.getDraws(), "D".equals(outcome) ? 1 : 0, stats::setDraws);
            addMerge(commands, match, team, "losses", stats.getLosses(), "L".equals(outcome) ? 1 : 0, stats::setLosses);
            addMerge(commands, match, team, "points", stats.getPoints(),
                    "W".equals(outcome) ? 3 : "D".equals(outcome) ? 1 : 0, stats::setPoints);
        }
    }

    private <T> void addMerge(List<FieldMergeCommand> commands, Match match, Team team,
                              String field, T current, T incoming, Consumer<T> setter) {
        if (incoming == null) return;
        commands.add(new FieldMergeCommand(match, Provider.FOOTBALL_DATA_UK,
                Dataset.MATCH_STATISTICS, "team:" + team.getId() + ":" + field,
                current != null, null, () -> setter.accept(incoming)));
    }

    private static Integer integer(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        try {
            double parsed = Double.parseDouble(raw.trim().replace(",", ""));
            if (!Double.isFinite(parsed) || parsed < 0 || parsed != Math.rint(parsed)
                    || parsed > Integer.MAX_VALUE) return null;
            return (int) parsed;
        } catch (NumberFormatException ex) { return null; }
    }

    private static BigDecimal decimalMetric(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        try {
            BigDecimal parsed = new BigDecimal(raw.trim());
            return parsed.signum() >= 0 ? parsed : null;
        } catch (NumberFormatException ex) { return null; }
    }

    private static String value(Map<String, String> row, String key) {
        return row.getOrDefault(key, "").trim();
    }

    private static String valueAny(Map<String, String> row, String... keys) {
        for (String key : keys) {
            String candidate = row.getOrDefault(key, "").trim();
            if (StringUtils.hasText(candidate)) return candidate;
        }
        return "";
    }

    private record StatsKey(Long matchId, Long teamId) {}
}
