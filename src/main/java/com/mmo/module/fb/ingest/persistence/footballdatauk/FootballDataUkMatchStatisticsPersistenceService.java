package com.mmo.module.fb.ingest.persistence.footballdatauk;

import com.mmo.module.fb.match.entity.Match;
import com.mmo.module.fb.match.entity.MatchAdvancedStats;
import com.mmo.module.fb.match.repository.MatchAdvancedStatsRepository;
import com.mmo.module.fb.match.service.MatchStatisticsSnapshotPersistenceService;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.team.entity.Team;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Maps Football-Data.co.uk team statistics into the shared MMO Core model.
 * Missing values remain null; free kicks conceded are never mislabeled as fouls.
 */
@Service
@RequiredArgsConstructor
public class FootballDataUkMatchStatisticsPersistenceService {

    private final MatchAdvancedStatsRepository statsRepository;
    private final MatchStatisticsSnapshotPersistenceService snapshotPersistenceService;

    @Transactional
    public int persist(Match match, Map<String, String> row) {
        int saved = 0;
        if (persistSide(match, match.getHomeTeam(), row, "H")) saved++;
        if (persistSide(match, match.getAwayTeam(), row, "A")) saved++;
        return saved;
    }

    private boolean persistSide(Match match, Team team, Map<String, String> row, String side) {
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
            return false;
        }

        snapshotPersistenceService.upsert(match, team, Provider.FOOTBALL_DATA_UK, row);

        MatchAdvancedStats stats = statsRepository.findByMatchIdAndTeamId(match.getId(), team.getId())
                .orElseGet(() -> MatchAdvancedStats.builder().match(match).team(team).build());
        boolean changed = false;

        changed |= setIfMissing(stats.getTotalShots(), shots, stats::setTotalShots);
        changed |= setIfMissing(stats.getShotsOnTarget(), onTarget, stats::setShotsOnTarget);
        changed |= setIfMissing(stats.getHitWoodwork(), hitWoodwork, stats::setHitWoodwork);
        changed |= setIfMissing(stats.getCornerKicks(), corners, stats::setCornerKicks);
        changed |= setIfMissing(stats.getFouls(), fouls, stats::setFouls);
        changed |= setIfMissing(stats.getFreeKicksConceded(), freeKicksConceded, stats::setFreeKicksConceded);
        changed |= setIfMissing(stats.getOffsides(), offsides, stats::setOffsides);
        changed |= setIfMissing(stats.getYellowCards(), yellow, stats::setYellowCards);
        changed |= setIfMissing(stats.getRedCards(), red, stats::setRedCards);
        changed |= setIfMissing(stats.getBookingPoints(), bookingPoints, stats::setBookingPoints);
        changed |= setIfMissing(stats.getXg(), xg, stats::setXg);
        changed |= setIfMissing(stats.getXga(), xga, stats::setXga);
        changed |= setIfMissing(stats.getScored(), scored, stats::setScored);

        // "missed" means missed chances; goals conceded must not be stored in that field.
        if (stats.getResult() == null && outcome != null) {
            stats.setResult(outcome);
            changed = true;
        }
        if (stats.getWins() == null && outcome != null) {
            stats.setWins("W".equals(outcome) ? 1 : 0);
            changed = true;
        }
        if (stats.getDraws() == null && outcome != null) {
            stats.setDraws("D".equals(outcome) ? 1 : 0);
            changed = true;
        }
        if (stats.getLosses() == null && outcome != null) {
            stats.setLosses("L".equals(outcome) ? 1 : 0);
            changed = true;
        }
        if (stats.getPoints() == null && outcome != null) {
            stats.setPoints("W".equals(outcome) ? 3 : "D".equals(outcome) ? 1 : 0);
            changed = true;
        }

        if (!changed) return false;
        // Canonical row is a merged analytics projection; source provenance is stored separately.
        statsRepository.save(stats);
        return true;
    }

    private boolean setIfMissing(Integer current, Integer incoming, Consumer<Integer> setter) {
        if (current != null || incoming == null) return false;
        setter.accept(incoming);
        return true;
    }

    private boolean setIfMissing(BigDecimal current, BigDecimal incoming,
                                 Consumer<BigDecimal> setter) {
        if (current != null || incoming == null) return false;
        setter.accept(incoming);
        return true;
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
