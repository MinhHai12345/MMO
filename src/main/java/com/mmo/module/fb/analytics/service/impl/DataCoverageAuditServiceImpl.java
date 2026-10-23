package com.mmo.module.fb.analytics.service.impl;

import com.mmo.AppProperties;
import com.mmo.module.fb.analytics.service.DataCoverageAuditService;
import com.mmo.module.fb.analytics.service.DataCoverageAuditService.*;
import com.mmo.module.fb.match.enums.MatchStatus;
import com.mmo.module.fb.ingest.enums.Provider;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DataCoverageAuditServiceImpl implements DataCoverageAuditService {
    private final EntityManager entityManager;
    private final AppProperties properties;

    @Override
    @Transactional(readOnly = true)
    public DataCoverageAuditReport runAudit() {
        List<AuditFinding> findings = new ArrayList<>();
        long leagues = count("select count(e) from League e");
        long activeLeagues = count("select count(e) from League e where e.isActive = true");
        long leaguesWithoutSeasons = count("select count(l) from League l where not exists (select s.id from Season s where s.league = l)");
        long seasons = count("select count(e) from Season e");
        long currentSeasons = count("select count(e) from Season e where e.isCurrent = true");
        long teams = count("select count(e) from Team e");
        long teamsWithoutMatches = count("select count(t) from Team t where not exists (select m.id from Match m where m.homeTeam = t or m.awayTeam = t)");
        long matches = count("select count(e) from Match e");
        long finished = count("select count(e) from Match e where e.status = :finished", MatchStatus.FINISHED);
        long scored = count("select count(e) from Match e where e.status = :finished and e.homeScore is not null and e.awayScore is not null", MatchStatus.FINISHED);
        long withXg = count("select count(e) from Match e where e.status = :finished and e.homeXG is not null and e.awayXG is not null", MatchStatus.FINISHED);

        Map<String, Long> standingCounts = new HashMap<>();
        for (Object[] row : entityManager.createQuery("select st.standingType, count(st) from LeagueStanding st group by st.standingType", Object[].class).getResultList()) {
            standingCounts.put(row[0] == null ? "UNKNOWN" : row[0].toString(), number(row[1]));
        }
        long standingRows = standingCounts.values().stream().mapToLong(Long::longValue).sum();

        List<Long> duplicateGroups = entityManager.createQuery("""
                select count(m) from Match m
                group by m.season.id, m.homeTeam.id, m.awayTeam.id, m.matchTime
                having count(m) > 1
                """, Long.class).getResultList();
        long duplicateExcess = duplicateGroups.stream().mapToLong(n -> n - 1).sum();

        Provider lp = properties.getIngestion().getProviders().getLeague();
        Provider tp = properties.getIngestion().getProviders().getTeam();
        Provider fp = properties.getIngestion().getProviders().getFixture();
        Provider sp = properties.getIngestion().getProviders().getStandings();
        Map<String, MappingCoverage> mapping = new LinkedHashMap<>();
        addMapping(mapping, "league", "League", "LeagueExternalId", "league", lp);
        addMapping(mapping, "team", "Team", "TeamExternalId", "team", tp);
        addMapping(mapping, "season", "Season", "SeasonExternalId", "season", lp);
        addMapping(mapping, "match", "Match", "MatchExternalId", "match", fp);

        List<SeasonCoverage> seasonCoverage = new ArrayList<>();
        List<String> configuredSeasons = properties.getFootballData() == null
                ? List.of() : properties.getFootballData().getSeasons();
        List<Object[]> seasonRows;
        if (configuredSeasons != null && !configuredSeasons.isEmpty()) {
            seasonRows = entityManager.createQuery("""
                    select s.id, l.name, s.year, s.isCurrent
                    from Season s join s.league l
                    where s.year in :years
                    order by l.name asc, s.year desc
                    """, Object[].class).setParameter("years", configuredSeasons).getResultList();
        } else {
            // Same default semantics as FootballData configuration: when no explicit years are set,
            // focus the season-level audit on seasons marked current instead of stale historical rows.
            seasonRows = entityManager.createQuery("""
                    select s.id, l.name, s.year, s.isCurrent
                    from Season s join s.league l
                    where s.isCurrent = true
                    order by l.name asc, s.year desc
                    """, Object[].class).getResultList();
        }
        for (Object[] s : seasonRows) {
            Long id = ((Number) s[0]).longValue();
            String league = (String) s[1];
            String year = (String) s[2];
            boolean current = Boolean.TRUE.equals(s[3]);
            Object[] m = entityManager.createQuery("""
                    select count(m),
                           sum(case when m.status = :finished then 1 else 0 end),
                           sum(case when m.status = :finished and m.homeScore is not null and m.awayScore is not null then 1 else 0 end),
                           sum(case when m.status = :finished and m.homeXG is not null and m.awayXG is not null then 1 else 0 end)
                    from Match m where m.season.id = :seasonId
                    """, Object[].class).setParameter("finished", MatchStatus.FINISHED)
                    .setParameter("seasonId", id).getSingleResult();
            long total = number(m[0]), seasonFinished = number(m[1]), seasonScored = number(m[2]), seasonXg = number(m[3]);
            Map<String, Long> byType = new HashMap<>();
            for (Object[] st : entityManager.createQuery("""
                    select st.standingType, count(st) from LeagueStanding st
                    where st.season.id = :seasonId group by st.standingType
                    """, Object[].class).setParameter("seasonId", id).getResultList()) {
                byType.put(st[0] == null ? "UNKNOWN" : st[0].toString(), number(st[1]));
            }
            long seasonStandingRows = byType.values().stream().mapToLong(Long::longValue).sum();
            long teamsInStandings = entityManager.createQuery("select count(distinct st.team.id) from LeagueStanding st where st.season.id = :seasonId", Long.class)
                    .setParameter("seasonId", id).getSingleResult();
            long totalRows = byType.getOrDefault("TOTAL", 0L);
            long homeRows = byType.getOrDefault("HOME", 0L);
            long awayRows = byType.getOrDefault("AWAY", 0L);
            List<String> issues = new ArrayList<>();
            if (total == 0) {
                issues.add("NO_FIXTURES");
                findings.add(new AuditFinding("WARNING", "SEASON_WITHOUT_FIXTURES", "Season has no stored fixtures.", 1, league + " " + year + " (seasonId=" + id + ")"));
            }
            if (seasonFinished > seasonScored) issues.add("FINISHED_MATCHES_MISSING_SCORES");
            if (!current && total > seasonFinished) {
                long unfinishedHistoricalFixtures = total - seasonFinished;
                issues.add("HISTORICAL_SEASON_HAS_UNFINISHED_FIXTURES");
                findings.add(new AuditFinding("WARNING", "HISTORICAL_SEASON_HAS_UNFINISHED_FIXTURES",
                        "A non-current season contains fixtures that are not marked FINISHED; inspect status and source data.",
                        unfinishedHistoricalFixtures, league + " " + year + " (stored=" + total
                        + ", finished=" + seasonFinished + ", seasonId=" + id + ")"));
            }
            if (seasonStandingRows == 0) {
                issues.add("NO_STANDINGS");
                findings.add(new AuditFinding("WARNING", "SEASON_WITHOUT_STANDINGS",
                        "Season has no stored standings.", 1, league + " " + year + " (seasonId=" + id + ")"));
            }
            if (seasonStandingRows > 0 && totalRows == 0) {
                issues.add("MISSING_TOTAL_STANDINGS");
                findings.add(new AuditFinding("WARNING", "SEASON_MISSING_TOTAL_STANDINGS",
                        "Season has standings rows but no TOTAL table.", 1, league + " " + year + " (seasonId=" + id + ")"));
            }
            if (totalRows > 0 && teamsInStandings > totalRows) issues.add("TOTAL_STANDINGS_MAY_BE_INCOMPLETE");
            if (seasonFinished > 0 && seasonScored == 0) issues.add("NO_FINISHED_MATCHES_WITH_SCORES");

            // These domestic leagues use a double round-robin format: every team hosts every other team once.
            // Do not apply this assumption to cup/knockout competitions such as the Champions League.
            long teamsInSeason = teamsInStandings > 0 ? teamsInStandings : countDistinctSeasonTeams(id);
            if (teamsInSeason < 2) teamsInSeason = expectedTeamCountForLeague(league);
            Long expectedMatches = expectedMatchesForSeason(league, teamsInSeason, year);
            // Repeated home/away pairings are only anomalous in a competition with a
            // single round-robin fixture per ordered pair. Cup/knockout competitions (for
            // example the Champions League) legitimately meet again in later rounds.
            // Championship season totals include playoffs, where league opponents can repeat;
            // skip pair-level warnings when the stored count exceeds the regular-season total.
            boolean auditRepeatedPairings = shouldAuditRepeatedPairings(league, total, teamsInSeason);
            List<Long> repeatedPairGroups = auditRepeatedPairings
                    ? entityManager.createQuery("""
                            select count(m) from Match m
                            where m.season.id = :seasonId
                            group by m.homeTeam.id, m.awayTeam.id
                            having count(m) > 1
                            """, Long.class).setParameter("seasonId", id).getResultList()
                    : List.of();
            long repeatedPairs = repeatedPairGroups.size();
            long matchCountDifference = expectedMatches == null ? 0 : total - expectedMatches;
            String integrityStatus = "NOT_APPLICABLE";
            if (expectedMatches != null) {
                integrityStatus = "OK";
                if (total > expectedMatches) {
                    integrityStatus = "EXCESS_FIXTURES";
                    issues.add("MATCH_COUNT_EXCEEDS_EXPECTED");
                    findings.add(new AuditFinding("WARNING", "SEASON_HAS_EXCESS_FIXTURES",
                            "Stored fixture count exceeds the double round-robin expectation.",
                            total - expectedMatches, league + " " + year + " (stored=" + total
                            + ", expected=" + expectedMatches + ", seasonId=" + id + ")"));
                } else if (!current && total < expectedMatches) {
                    integrityStatus = "INCOMPLETE_FIXTURES";
                    issues.add("FIXTURE_COVERAGE_BELOW_EXPECTED");
                    findings.add(new AuditFinding("WARNING", "SEASON_FIXTURE_COVERAGE_BELOW_EXPECTED",
                            "Historical season has fewer fixtures than the double round-robin expectation.",
                            expectedMatches - total, league + " " + year + " (stored=" + total
                            + ", expected=" + expectedMatches + ", seasonId=" + id + ")"));
                }
            }
            if (repeatedPairs > 0) {
                issues.add("REPEATED_HOME_AWAY_PAIRINGS");
                findings.add(new AuditFinding("WARNING", "REPEATED_HOME_AWAY_PAIRINGS",
                        "The same home/away team pairing occurs more than once in a season.",
                        repeatedPairs, league + " " + year + " (seasonId=" + id + ")"));
                if ("OK".equals(integrityStatus)) integrityStatus = "REPEATED_PAIRINGS";
            }
            seasonCoverage.add(new SeasonCoverage(id, league, year, current, total, seasonFinished, seasonScored,
                    seasonFinished - seasonScored, seasonXg, seasonStandingRows, totalRows, homeRows, awayRows,
                    teamsInStandings, expectedMatches, matchCountDifference, repeatedPairs, integrityStatus, issues));
        }

        addFinding(findings, leaguesWithoutSeasons, "WARNING", "LEAGUES_WITHOUT_SEASONS", "Leagues have no stored seasons.", "Review configured competitions and league ingestion.");
        addFinding(findings, teamsWithoutMatches, "WARNING", "TEAMS_WITHOUT_MATCHES", "Teams are not referenced by any fixture.", "Check team/fixture mapping and provider coverage.");
        addFinding(findings, finished - scored, "CRITICAL", "FINISHED_MATCHES_WITHOUT_SCORES", "FINISHED matches do not have both home and away scores.", "Correct status/score mapping before analytics or training.");
        addFinding(findings, finished - withXg, "INFO", "FINISHED_MATCHES_WITHOUT_XG", "Finished matches are missing xG for one or both teams.", "xG is optional for the baseline goals model.");
        addFinding(findings, duplicateGroups.size(), "CRITICAL", "DUPLICATE_FIXTURE_GROUPS", "Fixture signatures (season, home team, away team, kickoff) occur more than once.", "Inspect provider mappings and idempotent persistence before deleting records.");
        addFinding(findings, mapping.values().stream().mapToLong(MappingCoverage::missingMappings).sum(), "WARNING", "MISSING_PROVIDER_MAPPINGS", "Entities lack an external ID for the provider selected by their ingestion resource.", "Review per-entity mapping coverage; provider capabilities may differ.");
        addFinding(findings, standingRows == 0 && seasons > 0 ? seasons : 0, "WARNING", "NO_STANDINGS", "No standings are stored although seasons exist.", "Check provider support, season selection, and sync job results.");

        String status = findings.stream().anyMatch(f -> "CRITICAL".equals(f.severity())) ? "CRITICAL"
                : findings.stream().anyMatch(f -> "WARNING".equals(f.severity())) ? "WARNING" : "OK";
        Totals totals = new Totals(leagues, activeLeagues, leaguesWithoutSeasons, seasons, currentSeasons, teams,
                teamsWithoutMatches, matches, finished, scored, finished - scored, withXg, finished - withXg,
                standingRows, standingCounts.getOrDefault("TOTAL", 0L), standingCounts.getOrDefault("HOME", 0L),
                standingCounts.getOrDefault("AWAY", 0L), duplicateGroups.size(), duplicateExcess);
        return new DataCoverageAuditReport(Instant.now(), status, totals,
                new ProviderCoverage(lp.name(), tp.name(), fp.name(), sp.name(), mapping), seasonCoverage, findings);
    }

    private long countDistinctSeasonTeams(Long seasonId) {
        List<Long> homeTeams = entityManager.createQuery(
                "select distinct m.homeTeam.id from Match m where m.season.id = :seasonId", Long.class)
                .setParameter("seasonId", seasonId).getResultList();
        List<Long> awayTeams = entityManager.createQuery(
                "select distinct m.awayTeam.id from Match m where m.season.id = :seasonId", Long.class)
                .setParameter("seasonId", seasonId).getResultList();
        Set<Long> ids = new HashSet<>(homeTeams);
        ids.addAll(awayTeams);
        return ids.size();
    }

    boolean shouldAuditRepeatedPairings(String leagueName, long storedMatches, long teamCount) {
        if (!isDoubleRoundRobinLeague(leagueName) || teamCount < 2) return false;
        long regularSeasonMatches = teamCount * (teamCount - 1);
        // Playoff fixtures are included in Championship season totals and may repeat pairings.
        return !isChampionship(leagueName) || storedMatches <= regularSeasonMatches;
    }

    private boolean isChampionship(String leagueName) {
        return leagueName != null && leagueName.toLowerCase(Locale.ROOT).contains("championship");
    }

    private Long expectedMatchesForSeason(String leagueName, long teamCount, String seasonYear) {
        if (!isDoubleRoundRobinLeague(leagueName) || teamCount < 2) return null;
        long expected = teamCount * (teamCount - 1);
        if (leagueName != null && leagueName.toLowerCase(Locale.ROOT).contains("championship")) {
            // Championship counts include playoffs in the provider's competition fixture list.
            // Seasons through 2025/26 had 4-team, two-legged semi-finals + final (5 games).
            // From 2026/27, the EFL-approved format has 2 eliminators + two-legged semis + final (7 games).
            int startYear;
            try {
                startYear = Integer.parseInt(seasonYear);
            } catch (RuntimeException ignored) {
                return null;
            }
            expected += startYear >= 2026 ? 7 : 5;
        }
        return expected;
    }

    private long expectedTeamCountForLeague(String leagueName) {
        if (leagueName == null) return 0;
        String normalized = leagueName.toLowerCase(Locale.ROOT);
        if (normalized.contains("championship")) return 24;
        if (normalized.contains("bundesliga")) return 18;
        if (normalized.contains("premier league")
                || normalized.contains("primera division")
                || normalized.contains("serie a")) return 20;
        return 0;
    }

    private boolean isDoubleRoundRobinLeague(String leagueName) {
        if (leagueName == null) return false;
        String normalized = leagueName.toLowerCase(Locale.ROOT);
        return normalized.contains("premier league")
                || normalized.contains("championship")
                || normalized.contains("bundesliga")
                || normalized.contains("primera division")
                || normalized.contains("serie a");
    }

    private void addMapping(Map<String, MappingCoverage> result, String key, String entity,
                            String mappingEntity, String association, Provider provider) {
        long total = count("select count(e) from " + entity + " e");
        long mapped = count("select count(distinct m." + association + ".id) from " + mappingEntity + " m where m.provider = :provider", provider);
        long missing = count("select count(e) from " + entity + " e where not exists (select m.id from " + mappingEntity + " m where m." + association + " = e and m.provider = :provider)", provider);
        result.put(key, new MappingCoverage(total, mapped, missing));
    }

    private void addFinding(List<AuditFinding> out, long amount, String severity, String code, String message, String context) {
        if (amount > 0) out.add(new AuditFinding(severity, code, message, amount, context));
    }
    private long count(String jpql) { return entityManager.createQuery(jpql, Long.class).getSingleResult(); }
    private long count(String jpql, MatchStatus status) { return entityManager.createQuery(jpql, Long.class).setParameter("finished", status).getSingleResult(); }
    private long count(String jpql, Provider provider) { return entityManager.createQuery(jpql, Long.class).setParameter("provider", provider).getSingleResult(); }
    private long number(Object value) { return value instanceof Number n ? n.longValue() : 0L; }
}
