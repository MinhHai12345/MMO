package com.mmo.module.fb.ingest.persistence;

import com.mmo.module.fb.domain.match.entity.MatchExternalId;
import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.league.entity.Season;
import com.mmo.module.fb.domain.team.entity.Team;
import com.mmo.module.fb.domain.match.enums.MatchStatus;
import com.mmo.module.fb.domain.match.enums.MatchProvider;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.DataSourcePolicy;
import com.mmo.module.fb.ingest.policy.Dataset;
import com.mmo.module.fb.domain.match.repository.MatchExternalIdRepository;
import com.mmo.module.fb.domain.match.repository.MatchRepository;
import com.mmo.module.fb.domain.league.repository.SeasonRepository;
import com.mmo.module.fb.domain.team.repository.TeamRepository;
import com.mmo.module.fb.ingest.service.ExternalIdMappingService;
import com.mmo.module.fb.ingest.provider.model.ProviderFixture;
import com.mmo.module.fb.ingest.service.FixturePersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FixturePersistenceServiceImpl implements FixturePersistenceService {

    private final SeasonRepository seasonRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final MatchExternalIdRepository matchExternalIdRepository;
    private final ExternalIdMappingService externalIdMappingService;
    private final DataSourcePolicy dataSourcePolicy;

    @Override
    @Transactional
    public boolean persistFixture(Long seasonId, Long homeTeamId, Long awayTeamId,
                                  Provider provider, ProviderFixture fixture) {
        if (provider == null || fixture == null || fixture.externalId() == null
                || fixture.externalId().isBlank() || seasonId == null || homeTeamId == null || awayTeamId == null) {
            return false;
        }

        Season season = seasonRepository.findById(seasonId).orElse(null);
        Team home = teamRepository.findById(homeTeamId).orElse(null);
        Team away = teamRepository.findById(awayTeamId).orElse(null);
        if (season == null || home == null || away == null) return false;
        Match match = matchExternalIdRepository.findByProviderAndExternalId(MatchProvider.fromName(provider.name()), fixture.externalId())
                .map(MatchExternalId::getMatch)
                .orElse(null);

        if (match != null && (match.getSeason() == null
                || match.getHomeTeam() == null || match.getAwayTeam() == null
                || !match.getSeason().getId().equals(seasonId)
                || !match.getHomeTeam().getId().equals(homeTeamId)
                || !match.getAwayTeam().getId().equals(awayTeamId))) {
            log.warn("Provider {} fixture ID {} is already mapped to a different canonical fixture; skipping",
                    provider, fixture.externalId());
            return false;
        }

        if (match == null && fixture.kickoff() != null) {
            Instant windowStart = fixture.kickoff().minus(Duration.ofHours(6));
            Instant windowEnd = fixture.kickoff().plus(Duration.ofHours(6));
            List<Match> candidates = matchRepository.findFixtureCandidates(
                    seasonId, homeTeamId, awayTeamId, windowStart, windowEnd);
            if (candidates.size() == 1) {
                match = candidates.get(0);
            } else if (candidates.size() > 1) {
                log.warn("Ambiguous cross-provider fixture mapping for {} ({} vs {} at {})",
                        fixture.externalId(), home.getName(), away.getName(), fixture.kickoff());
                return false;
            }
        }

        var route = dataSourcePolicy.route(Dataset.FIXTURE, season);
        boolean primaryOwner = route.primary() == provider;
        boolean primaryIdentityExists = match != null && match.getId() != null && route.primary() != null
                && matchExternalIdRepository.existsByMatchIdAndProvider(
                        match.getId(), MatchProvider.fromName(route.primary().name()));
        boolean allowedFallback = dataSourcePolicy.mayFallbackForCore(
                provider, Dataset.FIXTURE, season, primaryIdentityExists);
        boolean secondaryMerge = match != null && route.secondary().contains(provider);
        if (!route.allows(provider) || (!primaryOwner && !allowedFallback && !secondaryMerge)) {
            log.debug("Rejecting fixture write outside provider route: provider={}, season={}, primaryIdentityExists={}",
                    provider, season.getYear(), primaryIdentityExists);
            return false;
        }

        if (match == null && provider != Provider.FOOTBALL_DATA
                && provider != Provider.UNDER_STATS && provider != Provider.FOOTBALL_DATA_UK) {
            log.debug("Skipping secondary fixture {} because no unique canonical fixture matched",
                    fixture.externalId());
            return false;
        }

        if (match == null && fixture.kickoff() == null) {
            log.warn("Skipping new fixture {} because kickoff time is missing", fixture.externalId());
            return false;
        }
        if (provider != Provider.FOOTBALL_DATA && provider != Provider.UNDER_STATS
                && provider != Provider.FOOTBALL_DATA_UK) {
            // Secondary providers may attach identity, but do not own Core fixture fields.
            externalIdMappingService.registerMatch(match, provider, fixture.externalId());
            return true;
        }

        boolean newMatch = match == null;
        if (newMatch) {
            if (fixture.kickoff() == null) return false;
            match = Match.builder().season(season).homeTeam(home).awayTeam(away)
                    .matchTime(fixture.kickoff()).status(MatchStatus.SCHEDULED).build();
            match = matchRepository.save(match);
        }

        mergeFixtureField(match, provider, "match_time", fixture.kickoff(),
                match.getMatchTime() != null && !newMatch, value -> match.setMatchTime((Instant) value));
        mergeFixtureField(match, provider, "round_info", fixture.round(),
                hasText(match.getRoundInfo()), value -> match.setRoundInfo((String) value));
        mergeFixtureField(match, provider, "venue", fixture.venue(),
                hasText(match.getVenue()), value -> match.setVenue((String) value));
        mergeFixtureField(match, provider, "referee", fixture.referee(),
                hasText(match.getReferee()), value -> match.setReferee((String) value));

        MatchStatus incomingStatus = toMatchStatus(fixture.status());
        if (incomingStatus != null && statusTransitionAllowed(match.getStatus(), incomingStatus)) {
            mergeFixtureField(match, provider, "status", incomingStatus,
                    match.getStatus() != null && !newMatch, value -> match.setStatus((MatchStatus) value));
        }
        if (fixture.homeScore() != null) mergeFixtureField(match, provider, "home_score",
                fixture.homeScore(), match.getHomeScore() != null, value -> match.setHomeScore((Integer) value));
        if (fixture.awayScore() != null) mergeFixtureField(match, provider, "away_score",
                fixture.awayScore(), match.getAwayScore() != null, value -> match.setAwayScore((Integer) value));

        matchRepository.save(match);
        externalIdMappingService.registerMatch(match, provider, fixture.externalId());
        return true;
    }


    private void mergeFixtureField(Match match, Provider provider, String field, Object incoming,
                                   boolean currentPresent, java.util.function.Consumer<Object> setter) {
        if (incoming == null) return;
        mergeService.merge(match, provider, Dataset.FIXTURE, field, currentPresent, null,
                () -> setter.accept(incoming));
    }

    private static boolean statusTransitionAllowed(MatchStatus current, MatchStatus incoming) {
        if (current == null) return true;
        if (current == MatchStatus.FINISHED && incoming != MatchStatus.FINISHED) return false;
        return current != MatchStatus.LIVE || incoming != MatchStatus.SCHEDULED;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static MatchStatus toMatchStatus(String status) {
        if (status == null || status.isBlank()) return null;
        return switch (status.toUpperCase()) {
            case "NS", "TBD", "SCHEDULED", "TIMED" -> MatchStatus.SCHEDULED;
            case "1H", "HT", "2H", "ET", "P", "LIVE", "BT", "IN_PLAY", "PAUSED" -> MatchStatus.LIVE;
            case "FT", "AET", "PEN", "FINISHED" -> MatchStatus.FINISHED;
            case "PST", "SUSP", "INT", "POSTPONED", "SUSPENDED" -> MatchStatus.POSTPONED;
            case "CANC", "ABD", "WO", "CANCELLED" -> MatchStatus.CANCELLED;
            default -> null;
        };
    }
    /**
     * Bounded transaction for fixture writes. The orchestration layer submits at most
     * 100 resolved fixtures per call so provider I/O and large season imports never
     * hold one transaction open for the whole dataset.
     */
    @Override
    @Transactional
    public int persistFixtures(Long seasonId, Provider provider,
                               List<com.mmo.module.fb.ingest.service.ResolvedProviderFixture> fixtures) {
        if (fixtures == null || fixtures.isEmpty()) return 0;
        int persisted = 0;
        for (var row : fixtures) {
            if (row == null) continue;
            if (persistFixture(seasonId, row.homeTeamId(), row.awayTeamId(), provider, row.fixture())) {
                persisted++;
            }
        }
        return persisted;
    }
}
