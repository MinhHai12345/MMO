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
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FixtureIngestionService {

    private final ProviderRegistry providerRegistry;
    private final AppProperties appProperties;
    private final LeagueIngestionService leagueIngestionService;
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
        List<Season> seasons = seasonRepository.findAll().stream()
                .filter(Season::isCurrent)
                .filter(season -> season.getLeague() != null && season.getYear() != null)
                .toList();

        for (Season season : seasons) {
            Long externalLeagueId = leagueExternalIdRepository
                    .findByLeagueIdAndProvider(season.getLeague().getId(), selectedProvider)
                    .map(LeagueExternalId::getExternalId)
                    .map(FixtureIngestionService::parseLong)
                    .orElse(null);
            if (externalLeagueId == null) continue;

            List<ProviderFixture> fixtures = providerRegistry.fixture(selectedProvider).fetchFixtures(
                    new FixtureQuery(externalLeagueId, season.getYear(), from, to));
            log.info("Fetched {} fixtures for league {} season {} from {}",
                    fixtures.size(), externalLeagueId, season.getYear(), selectedProvider);

            for (ProviderFixture fixture : fixtures) {
                Team home = findTeam(fixture.homeTeamExternalId(), selectedProvider);
                Team away = findTeam(fixture.awayTeamExternalId(), selectedProvider);
                if (home == null || away == null) {
                    skipped++;
                    log.debug("Skipping fixture {} because one or both teams are not mapped",
                            fixture.externalId());
                    continue;
                }

                Match match = matchExternalIdRepository
                        .findByProviderAndExternalId(selectedProvider, fixture.externalId())
                        .map(MatchExternalId::getMatch)
                        .orElse(null);

                if (match == null && fixture.kickoff() != null) {
                    List<Match> candidates = matchRepository.findAll().stream()
                            .filter(existing -> existing.getSeason() != null
                                    && existing.getSeason().getId().equals(season.getId()))
                            .filter(existing -> existing.getHomeTeam() != null
                                    && existing.getAwayTeam() != null
                                    && existing.getHomeTeam().getId().equals(home.getId())
                                    && existing.getAwayTeam().getId().equals(away.getId()))
                            .filter(existing -> existing.getMatchTime() != null
                                    && Duration.between(existing.getMatchTime(), fixture.kickoff())
                                    .abs().toHours() <= 6)
                            .toList();
                    if (candidates.size() == 1) {
                        match = candidates.get(0);
                    } else if (candidates.size() > 1) {
                        skipped++;
                        log.warn("Ambiguous cross-provider fixture mapping for {} ({} vs {} at {})",
                                fixture.externalId(), home.getName(), away.getName(), fixture.kickoff());
                        continue;
                    }
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
                saved++;
            }
        }

        log.info("Fixture ingestion via {} completed. {} saved/updated, {} skipped; date range {} to {}",
                selectedProvider, saved, skipped, from, to);
    }



    private Team findTeam(String externalId, Provider selectedProvider) {
        if (externalId == null || externalId.isBlank()) return null;
        return teamExternalIdRepository
                .findByProviderAndExternalId(selectedProvider, externalId)
                .map(TeamExternalId::getTeam)
                .orElse(null);
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
