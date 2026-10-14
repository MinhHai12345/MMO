package com.mmo.module.fb.ingest.service;

import com.mmo.AppProperties;
import com.mmo.module.fb.domain.entity.LeagueExternalId;
import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.entity.MatchExternalId;
import com.mmo.module.fb.domain.entity.Season;
import com.mmo.module.fb.domain.entity.Team;
import com.mmo.module.fb.domain.entity.TeamExternalId;
import com.mmo.module.fb.domain.enums.MatchStatus;
import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.domain.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.domain.repository.MatchExternalIdRepository;
import com.mmo.module.fb.domain.repository.MatchRepository;
import com.mmo.module.fb.domain.repository.SeasonRepository;
import com.mmo.module.fb.domain.repository.TeamExternalIdRepository;
import com.mmo.module.fb.ingest.provider.model.FixtureQuery;
import com.mmo.module.fb.ingest.provider.model.ProviderFixture;
import com.mmo.module.fb.ingest.provider.spi.ProviderRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FixtureIngestionService {

    private final ProviderRegistry providerRegistry;
    private final AppProperties appProperties;
    private final TeamIngestionService teamIngestionService;
    private final SeasonRepository seasonRepository;
    private final LeagueExternalIdRepository leagueExternalIdRepository;
    private final TeamExternalIdRepository teamExternalIdRepository;
    private final MatchExternalIdRepository matchExternalIdRepository;
    private final MatchRepository matchRepository;

    /**
     * Synchronizes current-season fixtures. Null date bounds mean a complete season bootstrap.
     * Provider-specific HTTP/DTO logic stays behind FixtureProvider.
     */
    @Transactional
    public void syncCurrentSeasonFixtures(LocalDate from, LocalDate to) {
        syncFixturesForProvider(appProperties.getIngestion().getProviders().getFixture(), from, to);
    }

    /**
     * Ensures upstream league/team IDs for the selected source before fetching fixtures.
     * The same canonical Match can then receive external IDs from multiple providers.
     */
    @Transactional
    public void syncFixturesForProvider(Provider selectedProvider, LocalDate from, LocalDate to) {
        if (selectedProvider == Provider.API_FOOTBALL
                && (appProperties.getApiFootball() == null || !appProperties.getApiFootball().isEnabled())) {
            return;
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must be before or equal to 'to'");
        }

        teamIngestionService.syncTeamsForProvider(selectedProvider);

        int saved = 0;
        int skipped = 0;
        List<Season> seasons = seasonRepository.findByIsCurrentTrueAndLeagueIsNotNullAndYearIsNotNull();
        Map<Long, String> leagueExternalIdsByLeagueId = new HashMap<>();
        leagueExternalIdRepository.findAllByProvider(selectedProvider).forEach(mapping ->
                leagueExternalIdsByLeagueId.put(mapping.getLeague().getId(), mapping.getExternalId()));
        Map<String, Team> teamsByExternalId = new HashMap<>();
        teamExternalIdRepository.findAllByProvider(selectedProvider).forEach(mapping ->
                teamsByExternalId.put(mapping.getExternalId(), mapping.getTeam()));
        Map<String, Match> matchesByExternalId = new HashMap<>();
        matchExternalIdRepository.findAllByProvider(selectedProvider).forEach(mapping ->
                matchesByExternalId.put(mapping.getExternalId(), mapping.getMatch()));

        for (Season season : seasons) {
            Long externalLeagueId = parseLong(leagueExternalIdsByLeagueId.get(season.getLeague().getId()));
            if (externalLeagueId == null) continue;

            List<ProviderFixture> fixtures = providerRegistry.fixture(selectedProvider).fetchFixtures(
                    new FixtureQuery(externalLeagueId, season.getYear(), from, to));
            log.info("Fetched {} fixtures for league {} season {} from {}",
                    fixtures.size(), externalLeagueId, season.getYear(), selectedProvider);

            for (ProviderFixture fixture : fixtures) {
                Team home = teamsByExternalId.get(fixture.homeTeamExternalId());
                Team away = teamsByExternalId.get(fixture.awayTeamExternalId());
                if (home == null || away == null) {
                    skipped++;
                    log.debug("Skipping fixture {} because one or both teams are not mapped",
                            fixture.externalId());
                    continue;
                }

                if (fixture.externalId() == null || fixture.externalId().isBlank()) {
                    skipped++;
                    log.warn("Skipping fixture with empty external ID from {}", selectedProvider);
                    continue;
                }

                Match match = matchesByExternalId.get(fixture.externalId());

                if (match != null && (!match.getSeason().getId().equals(season.getId())
                        || !match.getHomeTeam().getId().equals(home.getId())
                        || !match.getAwayTeam().getId().equals(away.getId()))) {
                    skipped++;
                    log.warn("Provider {} fixture ID {} is already mapped to a different canonical fixture; skipping",
                            selectedProvider, fixture.externalId());
                    continue;
                }

                if (match == null && fixture.kickoff() != null) {
                    Instant windowStart = fixture.kickoff().minus(Duration.ofHours(6));
                    Instant windowEnd = fixture.kickoff().plus(Duration.ofHours(6));
                    List<Match> candidates = matchRepository.findFixtureCandidates(
                            season.getId(), home.getId(), away.getId(), windowStart, windowEnd);
                    if (candidates.size() == 1) {
                        match = candidates.get(0);
                    } else if (candidates.size() > 1) {
                        skipped++;
                        log.warn("Ambiguous cross-provider fixture mapping for {} ({} vs {} at {})",
                                fixture.externalId(), home.getName(), away.getName(), fixture.kickoff());
                        continue;
                    }
                }

                if (match == null && fixture.kickoff() == null) {
                    skipped++;
                    log.warn("Skipping new fixture {} because kickoff time is missing", fixture.externalId());
                    continue;
                }
                if (match == null) {
                    match = new Match();
                    // Match.externalId is a legacy unique key; provider IDs live in MatchExternalId.
                    match.setExternalId(selectedProvider.name() + ":" + fixture.externalId());
                }

                match.setSeason(season);
                match.setHomeTeam(home);
                match.setAwayTeam(away);
                if (fixture.kickoff() != null) match.setMatchTime(fixture.kickoff());
                if (fixture.startTimestamp() != null) match.setStartTimestamp(fixture.startTimestamp());
                match.setRoundInfo(fixture.round());
                match.setVenue(fixture.venue());
                match.setReferee(fixture.referee());
                match.setStatus(toMatchStatus(fixture.status()));
                if (fixture.homeScore() != null || fixture.awayScore() != null) {
                    match.setHomeScore(fixture.homeScore());
                    match.setAwayScore(fixture.awayScore());
                }
                match = matchRepository.save(match);

                Match savedMatch = match;
                MatchExternalId externalId = matchExternalIdRepository
                        .findByMatchIdAndProvider(savedMatch.getId(), selectedProvider)
                        .orElseGet(() -> MatchExternalId.builder()
                                .match(savedMatch)
                                .provider(selectedProvider)
                                .externalId(fixture.externalId())
                                .build());
                externalId.setExternalId(fixture.externalId());
                matchExternalIdRepository.save(externalId);
                matchesByExternalId.put(fixture.externalId(), savedMatch);
                saved++;
            }
        }

        log.info("Fixture ingestion via {} completed. {} saved/updated, {} skipped; date range {} to {}",
                selectedProvider, saved, skipped, from, to);
    }



    private static MatchStatus toMatchStatus(String status) {
        if (status == null) return MatchStatus.SCHEDULED;
        return switch (status.toUpperCase()) {
            case "1H", "HT", "2H", "ET", "P", "LIVE", "BT" -> MatchStatus.LIVE;
            case "FT", "AET", "PEN" -> MatchStatus.FINISHED;
            case "PST", "SUSP", "INT" -> MatchStatus.POSTPONED;
            case "CANC", "ABD", "WO" -> MatchStatus.CANCELLED;
            default -> MatchStatus.SCHEDULED;
        };
    }

    private static Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
