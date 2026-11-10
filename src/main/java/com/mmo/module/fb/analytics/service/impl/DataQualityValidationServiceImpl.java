package com.mmo.module.fb.analytics.service.impl;

import com.mmo.module.fb.analytics.service.DataQualityValidationService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.mmo.module.fb.analytics.service.DataQualityValidationService.*;

@Service
@RequiredArgsConstructor
public class DataQualityValidationServiceImpl implements DataQualityValidationService {
    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public DataQualityValidationReport validate() {
        List<ValidationFinding> findings = new ArrayList<>();

        add(findings, "CRITICAL", "FINISHED_MATCH_MISSING_SCORE",
                "Finished fixtures must have both full-time scores.",
                "select count(m) from Match m where m.status = com.mmo.module.fb.domain.match.enums.MatchStatus.FINISHED and (m.homeScore is null or m.awayScore is null)");
        add(findings, "CRITICAL", "NEGATIVE_MATCH_SCORE",
                "Full-time and half-time scores cannot be negative.",
                "select count(m) from Match m where m.homeScore < 0 or m.awayScore < 0 or m.homeHalfTimeScore < 0 or m.awayHalfTimeScore < 0");
        add(findings, "WARNING", "HALF_TIME_SCORE_EXCEEDS_FULL_TIME",
                "A half-time score exceeds the corresponding full-time score.",
                "select count(m) from Match m where m.homeHalfTimeScore is not null and m.awayHalfTimeScore is not null and m.homeScore is not null and m.awayScore is not null and (m.homeHalfTimeScore > m.homeScore or m.awayHalfTimeScore > m.awayScore)");
        add(findings, "WARNING", "NEGATIVE_MATCH_XG",
                "Expected-goals values cannot be negative.",
                "select count(m) from Match m where m.homeXG < 0 or m.awayXG < 0");
        add(findings, "WARNING", "PARTIAL_MATCH_XG",
                "Only one side of a fixture has an expected-goals value.",
                "select count(m) from Match m where (m.homeXG is null and m.awayXG is not null) or (m.homeXG is not null and m.awayXG is null)");
        add(findings, "CRITICAL", "FIXTURE_TEAM_COLLISION",
                "A fixture has the same canonical team as both home and away.",
                "select count(m) from Match m where m.homeTeam.id = m.awayTeam.id");

        long duplicateExcess = entityManager.createQuery("""
                select count(m) from Match m
                group by m.season.id, m.homeTeam.id, m.awayTeam.id, m.matchTime
                having count(m) > 1
                """, Long.class).getResultList().stream().mapToLong(n -> n - 1).sum();
        add(findings, "CRITICAL", "DUPLICATE_FIXTURE",
                "The same season, home team, away team and kickoff timestamp occur more than once.",
                duplicateExcess);

        add(findings, "CRITICAL", "NEGATIVE_TEAM_MATCH_STAT",
                "Team-in-match statistics contain negative count values.",
                "select count(s) from MatchAdvancedStats s where s.ppdaAtt < 0 or s.ppdaDef < 0 "
                        + "or s.deepAllowed < 0 or s.scored < 0 or s.missed < 0 or s.wins < 0 "
                        + "or s.draws < 0 or s.losses < 0 or s.points < 0 or s.deepCompletions < 0 "
                        + "or s.finalThirdEntries < 0 or s.shotsFromBox < 0 or s.shotsOutsideBox < 0 "
                        + "or s.totalShots < 0 or s.shotsOnTarget < 0 or s.shotsOffTarget < 0 "
                        + "or s.blockedShots < 0 or s.cornerKicks < 0 or s.fouls < 0 "
                        + "or s.yellowCards < 0 or s.redCards < 0 or s.offsides < 0 "
                        + "or s.totalPasses < 0 or s.accuratePasses < 0 or s.goalkeeperSaves < 0 "
                        + "or s.bigChancesCreated < 0 or s.bigChancesMissed < 0");
        add(findings, "WARNING", "TEAM_STAT_RATE_OUT_OF_RANGE",
                "Possession or pass accuracy is outside the inclusive 0–100 range.",
                "select count(s) from MatchAdvancedStats s where (s.possession < 0 or s.possession > 100) or (s.passAccuracy < 0 or s.passAccuracy > 100)");
        add(findings, "WARNING", "NEGATIVE_TEAM_STAT_XG",
                "Team statistics contain negative xG-family values.",
                "select count(s) from MatchAdvancedStats s where s.xg < 0 or s.xga < 0 or s.npxg < 0 "
                        + "or s.npxga < 0 or s.expectedPoints < 0 or s.xgChain < 0 or s.xgBuildup < 0 "
                        + "or s.setPieceXg < 0 or s.counterAttackXg < 0");

        add(findings, "CRITICAL", "NEGATIVE_PLAYER_MATCH_STAT",
                "Player-match statistics contain negative count values.",
                "select count(s) from PlayerMatchStats s where s.minutesPlayed < 0 or s.goals < 0 "
                        + "or s.assists < 0 or s.shots < 0 or s.shotsOnTarget < 0 or s.passes < 0 "
                        + "or s.keyPasses < 0 or s.tackles < 0 or s.interceptions < 0 or s.duelsWon < 0 "
                        + "or s.dribbles < 0 or s.foulsDrawn < 0 or s.foulsCommitted < 0 "
                        + "or s.yellowCards < 0 or s.redCards < 0");
        add(findings, "WARNING", "PLAYER_MINUTES_OUT_OF_RANGE",
                "Player minutes exceed 130 for one fixture.",
                "select count(s) from PlayerMatchStats s where s.minutesPlayed > 130");
        add(findings, "WARNING", "PLAYER_RATE_OUT_OF_RANGE",
                "Player pass accuracy is outside 0–100 or player xG/xA is negative.",
                "select count(s) from PlayerMatchStats s where (s.passAccuracy < 0 or s.passAccuracy > 100) or s.xg < 0 or s.xa < 0");

        add(findings, "CRITICAL", "INVALID_STANDING_COUNTERS",
                "Standings contain negative points, match counters or goal totals.",
                "select count(s) from LeagueStanding s where s.points < 0 or s.played < 0 or s.won < 0 "
                        + "or s.drawn < 0 or s.lost < 0 or s.goalsFor < 0 or s.goalsAgainst < 0");
        add(findings, "CRITICAL", "STANDING_PLAYED_MISMATCH",
                "Played does not equal won + drawn + lost.",
                "select count(s) from LeagueStanding s where s.played is not null and s.won is not null "
                        + "and s.drawn is not null and s.lost is not null and s.played <> (s.won + s.drawn + s.lost)");
        add(findings, "WARNING", "STANDING_GOAL_DIFFERENCE_MISMATCH",
                "Goal difference does not equal goals for minus goals against.",
                "select count(s) from LeagueStanding s where s.goalDifference is not null and s.goalsFor is not null "
                        + "and s.goalsAgainst is not null and s.goalDifference <> (s.goalsFor - s.goalsAgainst)");

        add(findings, "CRITICAL", "NON_POSITIVE_NORMALIZED_ODDS",
                "Normalized odds quotes must have a strictly positive decimal odd.",
                "select count(q) from MatchOddsQuote q where q.odd <= 0");
        add(findings, "CRITICAL", "NON_POSITIVE_LEGACY_ODDS",
                "Populated legacy odds values must be strictly positive.",
                "select count(o) from MatchOdds o where "
                        + "(o.homeWinOdds is not null and o.homeWinOdds <= 0) or "
                        + "(o.drawOdds is not null and o.drawOdds <= 0) or "
                        + "(o.awayWinOdds is not null and o.awayWinOdds <= 0) or "
                        + "(o.over25Odds is not null and o.over25Odds <= 0) or "
                        + "(o.under25Odds is not null and o.under25Odds <= 0) or "
                        + "(o.bttsYesOdds is not null and o.bttsYesOdds <= 0) or "
                        + "(o.bttsNoOdds is not null and o.bttsNoOdds <= 0) or "
                        + "(o.asianHandicapHome is not null and o.asianHandicapHome <= 0) or "
                        + "(o.asianHandicapAway is not null and o.asianHandicapAway <= 0)");

        long critical = findings.stream().filter(f -> "CRITICAL".equals(f.severity())).count();
        long warnings = findings.stream().filter(f -> "WARNING".equals(f.severity())).count();
        String status = critical > 0 ? "CRITICAL" : warnings > 0 ? "WARNING" : "OK";
        return new DataQualityValidationReport(Instant.now(), status, critical, warnings, findings);
    }

    private void add(List<ValidationFinding> findings, String severity, String code,
                     String description, String jpql) {
        add(findings, severity, code, description, count(jpql));
    }

    private void add(List<ValidationFinding> findings, String severity, String code,
                     String description, long affectedRows) {
        if (affectedRows > 0) findings.add(new ValidationFinding(severity, code, description, affectedRows));
    }

    private long count(String jpql) {
        Long result = entityManager.createQuery(jpql, Long.class).getSingleResult();
        return result == null ? 0L : result;
    }
}
