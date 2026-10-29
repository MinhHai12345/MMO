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
import com.mmo.module.fb.team.repository.TeamRepository;
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
    private final TeamRepository teamRepository;
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
        int failed = 0;
        for (Season season : activeSeasons) {
            try {
                if (syncSeason(provider, season)) successful++;
            } catch (RuntimeException ex) {
                failed++;
                log.error("Understat sync failed for seasonId={} leagueId={} year={}: {}",
                        season.getId(), season.getLeague() == null ? null : season.getLeague().getId(),
                        season.getYear(), ex.getMessage(), ex);
            }
        }
        log.info("Understat sync finished: activeSeasons={}, successful={}, failed={}",
                activeSeasons.size(), successful, failed);
        if (failed > 0) {
            throw new IllegalStateException("Understat sync failed for " + failed
                    + " of " + activeSeasons.size() + " active seasons; inspect the per-season logs.");
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
        Team home = resolveTeam(source.homeTeamExternalId(), source.homeTeamName());
        Team away = resolveTeam(source.awayTeamExternalId(), source.awayTeamName());
        if (home == null || away == null || home.getId().equals(away.getId())) {
            log.debug("Skipping Understat match {} because canonical teams could not be uniquely resolved",
                    source.externalMatchId());
            return null;
        }

        Match match = matchExternalIdRepository.findByProviderAndExternalId(PROVIDER, source.externalMatchId())
                .map(MatchExternalId::getMatch)
                .orElse(null);
        Instant kickoff = source.kickoff() == null ? null : source.kickoff().toInstant(ZoneOffset.UTC);
        if (match == null && kickoff != null) {
            List<Match> candidates = matchRepository.findFixtureCandidates(
                    season.getId(), home.getId(), away.getId(),
                    kickoff.minus(FIXTURE_MATCH_WINDOW), kickoff.plus(FIXTURE_MATCH_WINDOW));
            if (candidates.size() == 1) match = candidates.get(0);
            else if (candidates.size() > 1) {
                log.warn("Ambiguous Understat fixture mapping for matchId={} seasonId={}",
                        source.externalMatchId(), season.getId());
                return null;
            }
        }
        if (match == null) {
            log.debug("Skipping Understat match {} because no canonical MMO fixture matched", source.externalMatchId());
            return null;
        }
        if (match.getSeason() == null || !season.getId().equals(match.getSeason().getId())
                || match.getHomeTeam() == null || match.getAwayTeam() == null
                || !home.getId().equals(match.getHomeTeam().getId())
                || !away.getId().equals(match.getAwayTeam().getId())) {
            log.warn("Understat match {} conflicts with canonical fixture identity; skipping", source.externalMatchId());
            return null;
        }

        try {
            externalIdMappingService.registerMatch(match, PROVIDER, source.externalMatchId());
        } catch (IllegalStateException conflict) {
            log.warn("Cannot register Understat match mapping {}: {}",
                    source.externalMatchId(), conflict.getMessage());
            return null;
        }
        if (source.homeXg() != null) match.setHomeXG(source.homeXg().doubleValue());
        if (source.awayXg() != null) match.setAwayXG(source.awayXg().doubleValue());
        if (match.getHomeScore() == null && source.homeScore() != null) match.setHomeScore(source.homeScore());
        if (match.getAwayScore() == null && source.awayScore() != null) match.setAwayScore(source.awayScore());
        return matchRepository.save(match);
    }

    private Map<String, Team> resolveLeagueTeams(List<ProviderMatchStatistics> matches) {
        Map<String, Team> result = new LinkedHashMap<>();
        for (ProviderMatchStatistics match : matches) {
            if (match == null) continue;
            addTeam(result, match.homeTeamExternalId(), match.homeTeamName());
            addTeam(result, match.awayTeamExternalId(), match.awayTeamName());
        }
        return result;
    }

    private void addTeam(Map<String, Team> teams, String externalId, String name) {
        if (!StringUtils.hasText(externalId)) return;
        Team team = resolveTeam(externalId, name);
        if (team != null) teams.putIfAbsent(externalId, team);
    }

    private Team resolveTeam(String externalId, String providerName) {
        if (!StringUtils.hasText(externalId)) return null;
        Team mapped = teamExternalIdRepository.findByProviderAndExternalId(PROVIDER, externalId)
                .map(TeamExternalId::getTeam).orElse(null);
        if (mapped != null) return mapped;
        if (!StringUtils.hasText(providerName)) return null;
        List<Team> exactMatches = teamRepository.findAllByNameIgnoreCase(providerName.trim());
        if (exactMatches.size() != 1) {
            log.debug("Understat team '{}' (external ID {}) has {} exact canonical name matches; skipping mapping",
                    providerName, externalId, exactMatches.size());
            return null;
        }
        Team team = exactMatches.get(0);
        try {
            externalIdMappingService.registerTeam(team, PROVIDER, externalId);
            return team;
        } catch (IllegalStateException conflict) {
            log.warn("Cannot register Understat team mapping {} ('{}'): {}",
                    externalId, providerName, conflict.getMessage());
            return null;
        }
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
        stats.setStatisticsProvider(PROVIDER.name());
        stats.setStatisticsSyncedAt(Instant.now());
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
