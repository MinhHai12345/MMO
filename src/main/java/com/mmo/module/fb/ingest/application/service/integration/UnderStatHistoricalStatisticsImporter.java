package com.mmo.module.fb.ingest.application.service.integration;

import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.application.port.out.HistoricalStatisticsIngestionPort;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.model.ProviderMatchStatistics;
import com.mmo.module.fb.ingest.provider.model.ProviderTeamMatchStats;
import com.mmo.module.fb.ingest.policy.Dataset;
import com.mmo.module.fb.ingest.application.port.out.MatchXgPersistencePort;
import com.mmo.module.fb.ingest.application.port.out.UnderStatHistoricalDataPort;
import com.mmo.module.fb.ingest.application.port.out.SourceAwareMergePort;
import com.mmo.module.fb.ingest.application.port.out.provider.HistoricalStatsIngestionProviderPort;
import com.mmo.module.fb.ingest.application.registry.ProviderIngestionRegistry;
import com.mmo.module.fb.ingest.application.port.out.ExternalIdMappingPort;
import com.mmo.module.fb.domain.league.entity.Season;
import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.match.entity.MatchAdvancedStats;
import com.mmo.module.fb.domain.match.service.MatchStatisticsSnapshotPersistenceService;
import com.mmo.module.fb.domain.match.enums.MatchProvider;
import com.mmo.module.fb.domain.team.entity.Team;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Consumer;

/**
 * Full persistence workflow for Understat's historical match and team-level metrics.
 * Understat is an enrichment source: canonical teams and matches are never created here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnderStatHistoricalStatisticsImporter implements HistoricalStatisticsIngestionPort {

    private static final Provider PROVIDER = Provider.UNDER_STATS;
    private static final Duration FIXTURE_MATCH_WINDOW = Duration.ofHours(6);
    private static final Pattern SEASON_START_YEAR = Pattern.compile("(\\d{4})");

    private final AppProperties appProperties;
    private final ProviderIngestionRegistry providerRegistry;
    private final UnderStatHistoricalDataPort historicalDataPort;
    private final MatchStatisticsSnapshotPersistenceService snapshotPersistenceService;
    private final ExternalIdMappingPort externalIdMappingService;
    private final MatchXgPersistencePort matchXgPersistenceService;
    private final SourceAwareMergePort sourceAwareMergeService;

    public void syncActiveSeasons() {
        AppProperties.ProviderConfig config = appProperties.getIngestion().getProviderConfig().get(PROVIDER);
        if (config == null || !config.isEnabled()) {
            log.info("Skipping UnderStat sync because provider {} is disabled", PROVIDER);
            return;
        }
        Provider selected = appProperties.getIngestion().getProviders().getHistoricalStats();
        if (selected != PROVIDER) {
            log.info("Skipping UnderStat sync because historical-stats provider is configured as {}", selected);
            return;
        }

        List<Season> activeSeasons = historicalDataPort.findActiveSeasons();
        if (activeSeasons.isEmpty()) {
            log.info("UnderStat sync has no active seasons. Set fb_seasons.is_active=true for seasons to ingest.");
            return;
        }

        HistoricalStatsIngestionProviderPort provider = providerRegistry.historicalStats(selected);
        int successful = 0;
        int skipped = 0;
        int failed = 0;
        for (Season season : activeSeasons) {
            try {
                if (syncSeason(provider, season)) successful++;
                else skipped++;
            } catch (RuntimeException ex) {
                failed++;
                log.error("UnderStat sync failed for seasonId={} leagueId={} year={}: {}",
                        season.getId(), season.getLeague() == null ? null : season.getLeague().getId(),
                        season.getYear(), ex.getMessage(), ex);
            }
        }
        log.info("UnderStat sync finished: activeSeasons={}, successful={}, skipped={}, failed={}",
                activeSeasons.size(), successful, skipped, failed);
        if (failed > 0 || skipped > 0 || successful == 0) {
            throw new IllegalStateException("UnderStat sync incomplete: activeSeasons=" + activeSeasons.size()
                    + ", successful=" + successful + ", skipped=" + skipped + ", failed=" + failed
                    + ". Check UNDER_STATS league identify mappings and per-season logs.");
        }
    }

    private boolean syncSeason(HistoricalStatsIngestionProviderPort provider, Season season) {
        if (season.getLeague() == null || season.getLeague().getId() == null) return false;
        String leagueIdentify = historicalDataPort
                .findLeagueIdentify(season.getLeague().getId(), PROVIDER)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .orElse(null);
        if (leagueIdentify == null) {
            log.warn("Skipping UnderStat seasonId={} because league {} has no UNDER_STATS identify mapping",
                    season.getId(), season.getLeague().getId());
            return false;
        }

        int providerYear = parseSeasonYear(season.getYear());
        List<ProviderMatchStatistics> matches = provider.fetchLeagueMatches(leagueIdentify, providerYear);
        int xgUpdated = 0;
        for (ProviderMatchStatistics source : matches) {
            if (source == null || !StringUtils.hasText(source.externalMatchId())) continue;
            Match persisted = persistMatchIdentityAndXg(season, source);
            if (persisted != null && (source.homeXg() != null || source.awayXg() != null)) xgUpdated++;
        }

        int teamStatsUpdated = 0;
        Map<String, Team> teams = resolveLeagueTeams(matches);
        for (Map.Entry<String, Team> teamEntry : teams.entrySet()) {
            String providerTeamId = teamEntry.getKey();
            Team team = teamEntry.getValue();
            List<ProviderTeamMatchStats> history =
                    provider.fetchTeamHistory(leagueIdentify, providerTeamId, providerYear);
            for (ProviderTeamMatchStats stats : history) {
                if (stats == null || !StringUtils.hasText(stats.externalMatchId())) continue;
                Match match = historicalDataPort.findMatch(PROVIDER, stats.externalMatchId()).orElse(null);
                if (match == null || match.getSeason() == null
                        || !season.getId().equals(match.getSeason().getId())) continue;
                persistTeamStats(match, team, stats);
                teamStatsUpdated++;
            }
        }

        log.info("UnderStat season sync complete: seasonId={}, leagueIdentify={}, year={}, sourceMatches={}, "
                        + "matchesWithXg={}, teamStatsRows={}",
                season.getId(), leagueIdentify, providerYear, matches.size(), xgUpdated, teamStatsUpdated);
        return true;
    }

    private Match persistMatchIdentityAndXg(Season season, ProviderMatchStatistics source) {
        Match match = historicalDataPort.findMatch(PROVIDER, source.externalMatchId()).orElse(null);
        Instant kickoff = source.kickoff() == null ? null : source.kickoff().toInstant(ZoneOffset.UTC);

        if (match == null && kickoff != null) {
            List<Match> candidates = historicalDataPort.findFixtureCandidates(
                    season.getId(), kickoff.minus(FIXTURE_MATCH_WINDOW), kickoff.plus(FIXTURE_MATCH_WINDOW));
            if (source.result() && source.homeScore() != null && source.awayScore() != null) {
                List<Match> scoreMatches = candidates.stream()
                        .filter(candidate -> source.homeScore().equals(candidate.getHomeScore())
                                && source.awayScore().equals(candidate.getAwayScore()))
                        .toList();
                if (!scoreMatches.isEmpty()) {
                    candidates = scoreMatches;
                } else if (candidates.size() != 1
                        || candidates.get(0).getHomeScore() != null
                        || candidates.get(0).getAwayScore() != null) {
                    candidates = List.of();
                }
            }
            if (candidates.size() == 1) {
                match = candidates.get(0);
            } else {
                log.debug("Skipping UnderStat match {}: {} canonical fixture candidates in season {}",
                        source.externalMatchId(), candidates.size(), season.getId());
                return null;
            }
        }

        if (match == null || match.getSeason() == null
                || !season.getId().equals(match.getSeason().getId())
                || match.getHomeTeam() == null || match.getAwayTeam() == null) {
            log.debug("Skipping UnderStat match {} because no canonical MMO fixture matched",
                    source.externalMatchId());
            return null;
        }

        if (source.result()
                && ((source.homeScore() != null && match.getHomeScore() != null
                        && !source.homeScore().equals(match.getHomeScore()))
                || (source.awayScore() != null && match.getAwayScore() != null
                        && !source.awayScore().equals(match.getAwayScore())))) {
            log.warn("UnderStat match {} scores conflict with canonical fixture {}; skipping",
                    source.externalMatchId(), match.getId());
            return null;
        }

        try {
            // The unique canonical fixture establishes the home/away identity. This avoids
            // guessing provider identity from team-name spelling or aliases.
            registerTeamIdentity(match.getHomeTeam(), source.homeTeamExternalId());
            registerTeamIdentity(match.getAwayTeam(), source.awayTeamExternalId());
            externalIdMappingService.registerMatch(match, PROVIDER, source.externalMatchId());
        } catch (IllegalStateException conflict) {
            log.warn("Cannot register UnderStat fixture/team mappings for match {}: {}",
                    source.externalMatchId(), conflict.getMessage());
            return null;
        }

        // Understat is an enrichment source, never the owner of canonical fixture scores.
        matchXgPersistenceService.upsert(PROVIDER, match, source.homeXg(), source.awayXg());
        return historicalDataPort.saveMatch(match);
    }

    private void registerTeamIdentity(Team team, String providerTeamId) {
        if (team == null || team.getId() == null || !StringUtils.hasText(providerTeamId)) {
            throw new IllegalStateException("Canonical team and UnderStat team ID are required");
        }
        externalIdMappingService.registerTeam(team, PROVIDER, providerTeamId);
    }

    private Map<String, Team> resolveLeagueTeams(List<ProviderMatchStatistics> matches) {
        Map<String, Team> result = new LinkedHashMap<>();
        for (ProviderMatchStatistics match : matches) {
            if (match == null) continue;
            addTeam(result, match.homeTeamExternalId());
            addTeam(result, match.awayTeamExternalId());
        }
        return result;
    }

    private void addTeam(Map<String, Team> teams, String externalId) {
        if (!StringUtils.hasText(externalId)) return;
        Team team = resolveTeam(externalId);
        if (team != null) teams.putIfAbsent(externalId, team);
    }

    private Team resolveTeam(String externalId) {
        if (!StringUtils.hasText(externalId)) return null;
        return historicalDataPort.findTeam(PROVIDER, externalId).orElse(null);
    }

    private void persistTeamStats(Match match, Team team, ProviderTeamMatchStats source) {
        snapshotPersistenceService.upsert(match, team, MatchProvider.fromName(PROVIDER.name()), source);
        MatchAdvancedStats stats = historicalDataPort
                .findTeamStatistics(match.getId(), team.getId())
                .orElseGet(() -> MatchAdvancedStats.builder().match(match).team(team).build());
        BigDecimal ppda = source.ppdaDef() == null || source.ppdaDef() == 0 || source.ppdaAtt() == null
                ? null : BigDecimal.valueOf(source.ppdaAtt())
                .divide(BigDecimal.valueOf(source.ppdaDef()), 3, RoundingMode.HALF_UP);
        boolean changed = false;
        changed |= mergeMetric(match, team, "xg", stats.getXg(), source.xg(), stats::setXg);
        changed |= mergeMetric(match, team, "xga", stats.getXga(), source.xga(), stats::setXga);
        changed |= mergeMetric(match, team, "npxg", stats.getNpxg(), source.npxg(), stats::setNpxg);
        changed |= mergeMetric(match, team, "npxga", stats.getNpxga(), source.npxga(), stats::setNpxga);
        changed |= mergeMetric(match, team, "ppda_att", stats.getPpdaAtt(), source.ppdaAtt(), stats::setPpdaAtt);
        changed |= mergeMetric(match, team, "ppda_def", stats.getPpdaDef(), source.ppdaDef(), stats::setPpdaDef);
        changed |= mergeMetric(match, team, "ppda", stats.getPpda(), ppda, stats::setPpda);
        changed |= mergeMetric(match, team, "deep_completions", stats.getDeepCompletions(), source.deep(), stats::setDeepCompletions);
        changed |= mergeMetric(match, team, "deep_allowed", stats.getDeepAllowed(), source.deepAllowed(), stats::setDeepAllowed);
        changed |= mergeMetric(match, team, "scored", stats.getScored(), source.scored(), stats::setScored);
        changed |= mergeMetric(match, team, "missed", stats.getMissed(), source.missed(), stats::setMissed);
        changed |= mergeMetric(match, team, "expected_points", stats.getExpectedPoints(), source.expectedPoints(), stats::setExpectedPoints);
        changed |= mergeMetric(match, team, "result", stats.getResult(), source.result(), stats::setResult);
        changed |= mergeMetric(match, team, "wins", stats.getWins(), source.wins(), stats::setWins);
        changed |= mergeMetric(match, team, "draws", stats.getDraws(), source.draws(), stats::setDraws);
        changed |= mergeMetric(match, team, "losses", stats.getLosses(), source.losses(), stats::setLosses);
        changed |= mergeMetric(match, team, "points", stats.getPoints(), source.points(), stats::setPoints);
        changed |= mergeMetric(match, team, "npx_goal_difference", stats.getNpxGoalDifference(),
                source.npxGoalDifference(), stats::setNpxGoalDifference);
        if (changed) historicalDataPort.saveTeamStatistics(stats);
    }

    private <T> boolean mergeMetric(Match match, Team team, String field, T current,
                                    T incoming, Consumer<T> setter) {
        if (incoming == null) return false;
        return sourceAwareMergeService.merge(match, PROVIDER, Dataset.MATCH_STATISTICS,
                "team:" + team.getId() + ":" + field, current != null, null, () -> setter.accept(incoming));
    }

    private static int parseSeasonYear(String year) {
        if (!StringUtils.hasText(year)) throw new IllegalArgumentException("Season year is blank");
        Matcher matcher = SEASON_START_YEAR.matcher(year);
        if (!matcher.find()) throw new IllegalArgumentException("Unsupported UnderStat season year: " + year);
        int parsed = Integer.parseInt(matcher.group(1));
        if (parsed < 2014) throw new IllegalArgumentException("UnderStat supports seasons from 2014 onward");
        return parsed;
    }
}
