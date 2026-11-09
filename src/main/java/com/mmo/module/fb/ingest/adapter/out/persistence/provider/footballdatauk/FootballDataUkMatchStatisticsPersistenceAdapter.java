package com.mmo.module.fb.ingest.adapter.out.persistence.provider.footballdatauk;

import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.ingest.application.port.out.FootballDataUkMatchStatisticsPersistencePort;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.DataSourcePolicy;
import com.mmo.module.fb.ingest.policy.Dataset;
import com.mmo.module.fb.ingest.adapter.out.persistence.core.SourceAwareMergeAdapter;
import com.mmo.module.fb.ingest.application.model.FieldMergeCommand;
import com.mmo.module.fb.domain.match.entity.MatchAdvancedStats;
import com.mmo.module.fb.domain.match.repository.MatchAdvancedStatsRepository;
import com.mmo.module.fb.domain.match.service.MatchStatisticsSnapshotPersistenceService;
import com.mmo.module.fb.domain.match.enums.MatchProvider;
import com.mmo.module.fb.domain.team.entity.Team;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Maps Football-Data.co.uk team statistics into the shared MMO Core model.
 * Missing values remain null; free kicks conceded are never mislabeled as fouls.
 */
@Repository
@RequiredArgsConstructor
public class FootballDataUkMatchStatisticsPersistenceAdapter implements FootballDataUkMatchStatisticsPersistencePort {

    private final MatchAdvancedStatsRepository statsRepository;
    private final MatchStatisticsSnapshotPersistenceService snapshotPersistenceService;
    private final DataSourcePolicy dataSourcePolicy;
    private final SourceAwareMergeAdapter sourceAwareMergeService;

    @Override
    @Transactional
    public int persist(Match match, String seasonYear, Map<String, String> row) {
        if (match == null || seasonYear == null || seasonYear.isBlank()
                || !dataSourcePolicy.isPrimary(
                        Provider.FOOTBALL_DATA_UK, Dataset.MATCH_STATISTICS, seasonYear)) {
            return 0;
        }

        // Collect every field mutation for both teams, then run the source/provenance
        // gate once. The previous implementation called merge() for every statistic,
        // repeating match locks, season routing and provenance SELECTs per field.
        List<FieldMergeCommand> commands = new ArrayList<>();
        List<MatchAdvancedStats> changedSides = new ArrayList<>(2);
        MatchAdvancedStats home = persistSide(match, match.getHomeTeam(), row, "H", commands);
        MatchAdvancedStats away = persistSide(match, match.getAwayTeam(), row, "A", commands);
        if (home != null) changedSides.add(home);
        if (away != null) changedSides.add(away);
        if (commands.isEmpty()) return 0;

        int applied = sourceAwareMergeService.mergeAll(commands);
        if (applied == 0) return 0;
        statsRepository.saveAll(changedSides);
        return changedSides.size();
    }

    private MatchAdvancedStats persistSide(Match match, Team team, Map<String, String> row,
                                           String side, List<FieldMergeCommand> commands) {
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
                && yellow == null && red == null && bookingPoints == null
                && xg == null && scored == null) {
            return null;
        }

        snapshotPersistenceService.upsert(match, team, MatchProvider.FOOTBALL_DATA_UK, row);
        MatchAdvancedStats stats = statsRepository.findByMatchIdAndTeamId(match.getId(), team.getId())
                .orElseGet(() -> MatchAdvancedStats.builder().match(match).team(team).build());

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
        return stats;
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
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static BigDecimal decimalMetric(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        try {
            BigDecimal parsed = new BigDecimal(raw.trim());
            return parsed.signum() >= 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
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
}
