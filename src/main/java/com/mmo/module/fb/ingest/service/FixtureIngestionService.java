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
import com.mmo.module.fb.ingest.provider.spi.FixtureProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FixtureIngestionService {

    private final FixtureProvider fixtureProvider;
    private final AppProperties appProperties;
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
        if (appProperties.getApiFootball() == null || !appProperties.getApiFootball().isEnabled()) {
            return;
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must be before or equal to 'to'");
        }

        int saved = 0;
        int skipped = 0;
        List<Season> seasons = seasonRepository.findAll().stream()
                .filter(Season::isCurrent)
                .filter(season -> season.getLeague() != null && season.getYear() != null)
                .toList();

        for (Season season : seasons) {
            Long externalLeagueId = leagueExternalIdRepository
                    .findByLeagueIdAndProvider(season.getLeague().getId(), Provider.API_FOOTBALL)
                    .map(LeagueExternalId::getExternalId)
                    .map(FixtureIngestionService::parseLong)
                    .orElse(null);
            if (externalLeagueId == null) continue;

            List<ProviderFixture> fixtures = fixtureProvider.fetchFixtures(
                    new FixtureQuery(externalLeagueId, season.getYear(), from, to));
            log.info("Fetched {} fixtures for league {} season {} from API-Football",
                    fixtures.size(), externalLeagueId, season.getYear());

            for (ProviderFixture fixture : fixtures) {
                Team home = findTeam(fixture.homeTeamExternalId());
                Team away = findTeam(fixture.awayTeamExternalId());
                if (home == null || away == null) {
                    skipped++;
                    log.debug("Skipping fixture {} because one or both teams are not mapped",
                            fixture.externalId());
                    continue;
                }

                Match match = matchExternalIdRepository
                        .findByProviderAndExternalId(Provider.API_FOOTBALL, fixture.externalId())
                        .map(MatchExternalId::getMatch)
                        .orElseGet(Match::new);

                match.setExternalId(fixture.externalId());
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
                        .findByMatchIdAndProvider(savedMatch.getId(), Provider.API_FOOTBALL)
                        .orElseGet(() -> MatchExternalId.builder()
                                .match(savedMatch)
                                .provider(Provider.API_FOOTBALL)
                                .externalId(fixture.externalId())
                                .build());
                externalId.setExternalId(fixture.externalId());
                matchExternalIdRepository.save(externalId);
                saved++;
            }
        }

        log.info("API-Football fixture ingestion completed. {} saved/updated, {} skipped; date range {} to {}",
                saved, skipped, from, to);
    }

    private Team findTeam(String externalId) {
        if (externalId == null || externalId.isBlank()) return null;
        return teamExternalIdRepository
                .findByProviderAndExternalId(Provider.API_FOOTBALL, externalId)
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
