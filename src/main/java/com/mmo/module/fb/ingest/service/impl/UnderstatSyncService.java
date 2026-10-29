package com.mmo.module.fb.ingest.service.impl;

import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.model.ProviderMatchStatistics;
import com.mmo.module.fb.ingest.provider.model.ProviderTeamMatchStats;
import com.mmo.module.fb.ingest.provider.spi.HistoricalStatsProvider;
import com.mmo.module.fb.ingest.provider.spi.ProviderRegistry;
import com.mmo.module.fb.ingest.service.ExternalIdMappingService;
import com.mmo.module.fb.league.entity.LeagueExternalId;
import com.mmo.module.fb.league.entity.Season;
import com.mmo.module.fb.league.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.league.repository.SeasonRepository;
import com.mmo.module.fb.match.entity.Match;
import com.mmo.module.fb.match.entity.MatchAdvancedStats;
import com.mmo.module.fb.match.entity.MatchExternalId;
import com.mmo.module.fb.match.repository.MatchAdvancedStatsRepository;
import com.mmo.module.fb.match.repository.MatchExternalIdRepository;
import com.mmo.module.fb.match.repository.MatchRepository;
import com.mmo.module.fb.team.entity.Team;
import com.mmo.module.fb.team.entity.TeamExternalId;
import com.mmo.module.fb.team.repository.TeamExternalIdRepository;
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

/**
 * Full persistence workflow for Understat's historical match and team-level metrics.
 * Understat is an enrichment source: canonical teams and matches are never created here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnderstatSyncService {

    private static final Provider PROVIDER = Provider.UNDER_STATS;
    private static final Duration FIXTURE_MATCH_WINDOW = Duration.ofHours(6);
    private static final Pattern SEASON_START_YEAR = Pattern.compile("(\\d{4})");

    private final AppProperties appProperties;
    private final ProviderRegistry providerRegistry;
    private final SeasonRepository seasonRepository;
    private final LeagueExternalIdRepository leagueExternalIdRepository;
    private final TeamExternalIdRepository teamExternalIdRepository;
    private final MatchRepository matchRepository;
    private final MatchExternalIdRepository matchExternalIdRepository;
    private final MatchAdvancedStatsRepository matchAdvancedStatsRepository;
    private final ExternalIdMappingService externalIdMappingService;

    public void syncActiveSeasons() {
        AppProperties.ProviderConfig config = appProperties.getIngestion().getProviderConfig().get(PROVIDER);
        if (config == null || !config.isEnabled()) {
            log.info("Skipping Understat sync because provider {} is disabled", PROVIDER);
            return;
        }
        Provider selected = appProperties.getIngestion().getProviders().getHistoricalStats();
        if (selected != PROVIDER) {
            log.info("Skipping Understat sync because historical-stats provider is configured as {}", selected);
            return;
        }

        List<Season> activeSeasons = seasonRepository.findByIsActiveTrueAndLeagueIsNotNullAndYearIsNotNull();
        if (activeSeasons.isEmpty()) {
            log.info("Understat sync has no active seasons. Set fb_seasons.is_active=true for seasons to ingest.");
            return;
        }

        HistoricalStatsProvider provider = providerRegistry.historicalStats(selected);
        int successful = 0;
        int skipped = 0;
        int failed = 0;
        for (Season season : activeSeasons) {
            try {
                if (syncSeason(provider, season)) successful++;
                else skipped++;
            } catch (RuntimeException ex) {
                failed++;
                log.error("Understat sync failed for seasonId={} leagueId={} year={}: {}",
                        season.getId(), season.getLeague() == null ? null : season.getLeague().getId(),
                        season.getYear(), ex.getMessage(), ex);
            }
        }
        log.info("Understat sync finished: activeSeasons={}, successful={}, skipped={}, failed={}",
                activeSeasons.size(), successful, skipped, failed);
        if (failed > 0 || skipped > 0 || successful == 0) {
            throw new IllegalStateException("Understat sync incomplete: activeSeasons=" + activeSeasons.size()
                    + ", successful=" + successful + ", skipped=" + skipped + ", failed=" + failed
                    + ". Check UNDER_STATS league identify mappings and per-season logs.");
        }
    }

    private boolean syncSeason(HistoricalStatsProvider provider, Season season) {
        if (season.getLeague() == null || season.getLeague().getId() == null) return false;
        LeagueExternalId mapping = leagueExternalIdRepository
                .findByLeagueIdAndProvider(season.getLeague().getId(), PROVIDER)
                .orElse(null);
        if (mapping == null || !StringUtils.hasText(mapping.getIdentify())) {
            log.warn("Skipping Understat seasonId={} because league {} has no UNDER_STATS identify mapping",
                    season.getId(), season.getLeague().getId());
            return false;
        }

        int providerYear = parseSeasonYear(season.getYear());
        String leagueIdentify = mapping.getIdentify().trim();
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
                Match match = matchExternalIdRepository
                        .findByProviderAndExternalId(PROVIDER, stats.externalMatchId())
                        .map(MatchExternalId::getMatch)
                        .orElse(null);
                if (match == null || match.getSeason() == null
                        || !season.getId().equals(match.getSeason().getId())) continue;
                persistTeamStats(match, team, stats);
                teamStatsUpdated++;
            }
        }

        log.info("Understat season sync complete: seasonId={}, leagueIdentify={}, year={}, sourceMatches={}, "
                        + "matchesWithXg={}, teamStatsRows={}",
                season.getId(), leagueIdentify, providerYear, matches.size(), xgUpdated, teamStatsUpdated);
        return true;
    }

    private Match persistMatchIdentityAndXg(Season season, ProviderMatchStatistics source) {
        Match match = matchExternalIdRepository.findByProviderAndExternalId(PROVIDER, source.externalMatchId())
                .map(MatchExternalId::getMatch)
                .orElse(null);
        Instant kickoff = source.kickoff() == null ? null : source.kickoff().toInstant(ZoneOffset.UTC);

        if (match == null && kickoff != null) {
            List<Match> candidates = matchRepository.findFixtureCandidatesBySeasonAndTime(
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
                log.debug("Skipping Understat match {}: {} canonical fixture candidates in season {}",
                        source.externalMatchId(), candidates.size(), season.getId());
                return null;
            }
        }

        if (match == null || match.getSeason() == null
                || !season.getId().equals(match.getSeason().getId())
                || match.getHomeTeam() == null || match.getAwayTeam() == null) {
            log.debug("Skipping Understat match {} because no canonical MMO fixture matched",
                    source.externalMatchId());
            return null;
        }

        if (source.result()
                && ((source.homeScore() != null && match.getHomeScore() != null
                        && !source.homeScore().equals(match.getHomeScore()))
                || (source.awayScore() != null && match.getAwayScore() != null
                        && !source.awayScore().equals(match.getAwayScore())))) {
            log.warn("Understat match {} scores conflict with canonical fixture {}; skipping",
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
            log.warn("Cannot register Understat fixture/team mappings for match {}: {}",
                    source.externalMatchId(), conflict.getMessage());
            return null;
        }

        if (source.homeXg() != null) match.setHomeXG(source.homeXg().doubleValue());
        if (source.awayXg() != null) match.setAwayXG(source.awayXg().doubleValue());
        if (match.getHomeScore() == null && source.homeScore() != null) match.setHomeScore(source.homeScore());
        if (match.getAwayScore() == null && source.awayScore() != null) match.setAwayScore(source.awayScore());
        return matchRepository.save(match);
    }

    private void registerTeamIdentity(Team team, String providerTeamId) {
        if (team == null || team.getId() == null || !StringUtils.hasText(providerTeamId)) {
            throw new IllegalStateException("Canonical team and Understat team ID are required");
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
        return teamExternalIdRepository.findByProviderAndExternalId(PROVIDER, externalId)
                .map(TeamExternalId::getTeam)
                .orElse(null);
    }

    private void persistTeamStats(Match match, Team team, ProviderTeamMatchStats source) {
        MatchAdvancedStats stats = matchAdvancedStatsRepository
                .findByMatchIdAndTeamId(match.getId(), team.getId())
                .orElseGet(() -> MatchAdvancedStats.builder().match(match).team(team).build());
        stats.setXg(source.xg());
        stats.setXga(source.xga());
        stats.setNpxg(source.npxg());
        stats.setNpxga(source.npxga());
        stats.setPpdaAtt(source.ppdaAtt());
        stats.setPpdaDef(source.ppdaDef());
        stats.setPpda(source.ppdaDef() == null || source.ppdaDef() == 0 || source.ppdaAtt() == null
                ? null : BigDecimal.valueOf(source.ppdaAtt())
                .divide(BigDecimal.valueOf(source.ppdaDef()), 3, RoundingMode.HALF_UP));
        stats.setDeepCompletions(source.deep());
        stats.setDeepAllowed(source.deepAllowed());
        stats.setScored(source.scored());
        stats.setMissed(source.missed());
        stats.setExpectedPoints(source.expectedPoints());
        stats.setResult(source.result());
        stats.setWins(source.wins());
        stats.setDraws(source.draws());
        stats.setLosses(source.losses());
        stats.setPoints(source.points());
        stats.setNpxGoalDifference(source.npxGoalDifference());
        Instant syncedAt = Instant.now();
        if (stats.getId() == null || !StringUtils.hasText(stats.getStatisticsProvider())) {
            stats.setStatisticsProvider(PROVIDER.name());
            stats.setStatisticsSyncedAt(syncedAt);
        }
        stats.setUnderstatSyncedAt(syncedAt);
        matchAdvancedStatsRepository.save(stats);
        match.setStatisticsLastAttemptAt(Instant.now());
        matchRepository.save(match);
    }

    private static int parseSeasonYear(String year) {
        if (!StringUtils.hasText(year)) throw new IllegalArgumentException("Season year is blank");
        Matcher matcher = SEASON_START_YEAR.matcher(year);
        if (!matcher.find()) throw new IllegalArgumentException("Unsupported Understat season year: " + year);
        int parsed = Integer.parseInt(matcher.group(1));
        if (parsed < 2014) throw new IllegalArgumentException("Understat supports seasons from 2014 onward");
        return parsed;
    }
}
