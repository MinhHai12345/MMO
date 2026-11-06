package com.mmo.module.fb.ingest.adapter.out.persistence.core;

import com.mmo.module.fb.domain.league.entity.Season;
import com.mmo.module.fb.domain.league.repository.SeasonRepository;
import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.match.entity.MatchExternalId;
import com.mmo.module.fb.domain.match.enums.MatchProvider;
import com.mmo.module.fb.domain.match.enums.MatchStatus;
import com.mmo.module.fb.domain.match.repository.MatchExternalIdRepository;
import com.mmo.module.fb.domain.match.repository.MatchRepository;
import com.mmo.module.fb.domain.team.entity.Team;
import com.mmo.module.fb.domain.team.repository.TeamRepository;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.DataSourcePolicy;
import com.mmo.module.fb.ingest.policy.Dataset;
import com.mmo.module.fb.ingest.application.port.out.FixturePersistencePort;
import com.mmo.module.fb.ingest.application.model.ResolvedProviderFixture;
import com.mmo.module.fb.ingest.provider.model.ProviderFixture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FixturePersistenceAdapter implements FixturePersistencePort {

    private static final Duration MATCH_TIME_TOLERANCE = Duration.ofHours(6);

    private final SeasonRepository seasonRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final MatchExternalIdRepository matchExternalIdRepository;
    private final DataSourcePolicy dataSourcePolicy;
    private final SourceAwareMergeAdapter mergeService;

    @Override
    @Transactional
    public boolean persistFixture(Long seasonId, Long homeTeamId, Long awayTeamId,
                                  Provider provider, ProviderFixture fixture) {
        return persistFixtures(seasonId, provider,
                List.of(new ResolvedProviderFixture(homeTeamId, awayTeamId, fixture))) == 1;
    }

    /**
     * Performs one bounded batch read phase, then merges and writes the chunk in one
     * transaction. Provider identity and candidate-match lookups are never performed
     * from the per-row merge loop.
     */
    @Override
    @Transactional
    public int persistFixtures(Long seasonId, Provider provider, List<ResolvedProviderFixture> rows) {
        if (seasonId == null || provider == null || rows == null || rows.isEmpty()) return 0;

        List<ResolvedProviderFixture> validRows = rows.stream()
                .filter(Objects::nonNull)
                .filter(row -> row.fixture() != null && hasText(row.fixture().externalId())
                        && row.homeTeamId() != null && row.awayTeamId() != null
                        && !row.homeTeamId().equals(row.awayTeamId()))
                .toList();
        if (validRows.isEmpty()) return 0;

        Season season = seasonRepository.findById(seasonId).orElse(null);
        if (season == null) return 0;

        Set<Long> teamIds = validRows.stream()
                .flatMap(row -> java.util.stream.Stream.of(row.homeTeamId(), row.awayTeamId()))
                .collect(Collectors.toSet());
        Map<Long, Team> teams = teamRepository.findAllById(teamIds).stream()
                .collect(Collectors.toMap(Team::getId, team -> team));

        List<String> externalIds = validRows.stream().map(row -> row.fixture().externalId().trim())
                .distinct().toList();
        MatchProvider domainProvider = MatchProvider.fromName(provider.name());
        Map<String, MatchExternalId> externalIdentityById =
                matchExternalIdRepository.findAllByProviderAndExternalIdIn(domainProvider, externalIds).stream()
                        .collect(Collectors.toMap(MatchExternalId::getExternalId, mapping -> mapping,
                                (left, right) -> left));

        Set<Long> mappedMatchIds = externalIdentityById.values().stream()
                .map(mapping -> mapping.getMatch().getId()).filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Instant minKickoff = validRows.stream().map(row -> row.fixture().kickoff())
                .filter(Objects::nonNull).min(Instant::compareTo).orElse(null);
        Instant maxKickoff = validRows.stream().map(row -> row.fixture().kickoff())
                .filter(Objects::nonNull).max(Instant::compareTo).orElse(null);
        List<Match> candidates = new ArrayList<>();
        if (minKickoff != null && !teamIds.isEmpty()) {
            candidates = matchRepository.findBatchFixtureCandidates(seasonId, new ArrayList<>(teamIds),
                    minKickoff.minus(MATCH_TIME_TOLERANCE), maxKickoff.plus(MATCH_TIME_TOLERANCE));
        }

        Set<Long> candidateMatchIds = candidates.stream().map(Match::getId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        candidateMatchIds.addAll(mappedMatchIds);
        Map<Long, MatchExternalId> providerIdentityByMatchId = candidateMatchIds.isEmpty()
                ? new HashMap<>()
                : matchExternalIdRepository.findAllByMatchIdInAndProvider(
                        new ArrayList<>(candidateMatchIds), domainProvider).stream()
                        .collect(Collectors.toMap(mapping -> mapping.getMatch().getId(), mapping -> mapping,
                                (left, right) -> left));

        Provider primaryProvider = dataSourcePolicy.route(Dataset.FIXTURE, season).primary();
        Set<Long> primaryMappedMatchIds = Set.of();
        if (primaryProvider != null && primaryProvider != provider && !candidateMatchIds.isEmpty()) {
            primaryMappedMatchIds = matchExternalIdRepository.findAllByMatchIdInAndProvider(
                            new ArrayList<>(candidateMatchIds), MatchProvider.fromName(primaryProvider.name()))
                    .stream().map(mapping -> mapping.getMatch().getId()).collect(Collectors.toSet());
        }

        Map<String, Long> seenExternalIds = new HashMap<>();
        int persisted = 0;
        List<MatchExternalId> mappingsToInsert = new ArrayList<>();
        Set<Long> matchesWritten = new HashSet<>();

        for (ResolvedProviderFixture row : validRows) {
            Team home = teams.get(row.homeTeamId());
            Team away = teams.get(row.awayTeamId());
            if (home == null || away == null) continue;
            ProviderFixture fixture = row.fixture();
            String externalId = fixture.externalId().trim();

            Long previousRowMatch = seenExternalIds.get(externalId);
            if (previousRowMatch != null) {
                log.warn("Skipping duplicate provider fixture ID {} within batch {}", externalId, provider);
                continue;
            }

            MatchExternalId knownIdentity = externalIdentityById.get(externalId);
            Match match = knownIdentity == null ? null : knownIdentity.getMatch();
            if (match != null && !sameCanonicalFixture(match, seasonId, home.getId(), away.getId())) {
                log.warn("Provider {} fixture ID {} is already mapped to a different canonical fixture",
                        provider, externalId);
                continue;
            }

            if (match == null && fixture.kickoff() != null) {
                List<Match> exactCandidates = candidates.stream()
                        .filter(candidate -> sameCanonicalFixture(candidate, seasonId, home.getId(), away.getId()))
                        .filter(candidate -> candidate.getMatchTime() != null
                                && Duration.between(candidate.getMatchTime(), fixture.kickoff()).abs()
                                .compareTo(MATCH_TIME_TOLERANCE) <= 0)
                        .toList();
                if (exactCandidates.size() == 1) {
                    match = exactCandidates.get(0);
                } else if (exactCandidates.size() > 1) {
                    log.warn("Ambiguous cross-provider fixture mapping for provider={} externalId={}",
                            provider, externalId);
                    continue;
                }
            }

            boolean primaryOwner = primaryProvider == provider;
            boolean primaryIdentityExists = match != null && primaryMappedMatchIds.contains(match.getId());
            boolean allowedFallback = dataSourcePolicy.mayFallbackForCore(
                    provider, Dataset.FIXTURE, season, primaryIdentityExists);
            boolean secondaryMerge = match != null
                    && dataSourcePolicy.route(Dataset.FIXTURE, season).secondary().contains(provider);
            if (!dataSourcePolicy.isAllowed(provider, Dataset.FIXTURE, season)
                    || (!primaryOwner && !allowedFallback && !secondaryMerge)) continue;

            if (match == null && !ownsCoreFixtures(provider)) continue;
            if (match == null && fixture.kickoff() == null) continue;

            // A routed secondary source may attach its provider identity after the
            // primary identity exists, but must not enter the canonical core-field path.
            if (!primaryOwner && !allowedFallback) {
                if (match == null) continue;
                MatchExternalId existingForMatch = providerIdentityByMatchId.get(match.getId());
                if (existingForMatch != null && !existingForMatch.getExternalId().equals(externalId)) {
                    log.warn("Provider {} already has a different fixture ID for canonical match {}",
                            provider, match.getId());
                    continue;
                }
                if (knownIdentity == null) mappingsToInsert.add(newMapping(match, domainProvider, externalId));
                providerIdentityByMatchId.put(match.getId(),
                        knownIdentity != null ? knownIdentity : newMapping(match, domainProvider, externalId));
                seenExternalIds.put(externalId, match.getId());
                persisted++;
                continue;
            }

            if (!ownsCoreFixtures(provider)) {
                if (match == null) continue;
                MatchExternalId existingForMatch = providerIdentityByMatchId.get(match.getId());
                if (existingForMatch != null && !existingForMatch.getExternalId().equals(externalId)) {
                    log.warn("Provider {} already has a different fixture ID for canonical match {}",
                            provider, match.getId());
                    continue;
                }
                if (knownIdentity == null) mappingsToInsert.add(newMapping(match, domainProvider, externalId));
                providerIdentityByMatchId.put(match.getId(),
                        knownIdentity != null ? knownIdentity : newMapping(match, domainProvider, externalId));
                seenExternalIds.put(externalId, match.getId());
                persisted++;
                continue;
            }

            MatchExternalId existingForMatch = match == null ? null : providerIdentityByMatchId.get(match.getId());
            if (existingForMatch != null && !existingForMatch.getExternalId().equals(externalId)) {
                log.warn("Provider {} already has a different fixture ID for canonical match {}",
                        provider, match.getId());
                continue;
            }

            boolean isNew = match == null;
            if (isNew) {
                match = Match.builder().season(season).homeTeam(home).awayTeam(away)
                        .matchTime(fixture.kickoff()).status(MatchStatus.SCHEDULED).build();
                match = matchRepository.save(match);
            }
            Match target = match;
            mergeFixtureField(target, provider, "match_time", fixture.kickoff(),
                    target.getMatchTime() != null && !isNew, value -> target.setMatchTime((Instant) value));
            mergeFixtureField(target, provider, "round_info", fixture.round(),
                    hasText(target.getRoundInfo()), value -> target.setRoundInfo((String) value));
            mergeFixtureField(target, provider, "venue", fixture.venue(),
                    hasText(target.getVenue()), value -> target.setVenue((String) value));
            mergeFixtureField(target, provider, "referee", fixture.referee(),
                    hasText(target.getReferee()), value -> target.setReferee((String) value));

            MatchStatus incomingStatus = toMatchStatus(fixture.status());
            if (incomingStatus != null && statusTransitionAllowed(target.getStatus(), incomingStatus)) {
                mergeFixtureField(target, provider, "status", incomingStatus,
                        target.getStatus() != null && !isNew, value -> target.setStatus((MatchStatus) value));
            }
            if (fixture.homeScore() != null) mergeFixtureField(target, provider, "home_score",
                    fixture.homeScore(), target.getHomeScore() != null, value -> target.setHomeScore((Integer) value));
            if (fixture.awayScore() != null) mergeFixtureField(target, provider, "away_score",
                    fixture.awayScore(), target.getAwayScore() != null, value -> target.setAwayScore((Integer) value));

            if (matchesWritten.add(target.getId())) matchRepository.save(target);
            if (knownIdentity == null) {
                mappingsToInsert.add(newMapping(target, domainProvider, externalId));
                providerIdentityByMatchId.put(target.getId(), newMapping(target, domainProvider, externalId));
            }
            seenExternalIds.put(externalId, target.getId());
            persisted++;
        }

        if (!mappingsToInsert.isEmpty()) matchExternalIdRepository.saveAll(mappingsToInsert);
        return persisted;
    }

    private static MatchExternalId newMapping(Match match, MatchProvider provider, String externalId) {
        return MatchExternalId.builder().match(match).provider(provider).externalId(externalId).build();
    }

    private static boolean sameCanonicalFixture(Match match, Long seasonId, Long homeTeamId, Long awayTeamId) {
        return match != null && match.getSeason() != null && match.getSeason().getId().equals(seasonId)
                && match.getHomeTeam() != null && match.getHomeTeam().getId().equals(homeTeamId)
                && match.getAwayTeam() != null && match.getAwayTeam().getId().equals(awayTeamId);
    }

    private static boolean ownsCoreFixtures(Provider provider) {
        return provider == Provider.FOOTBALL_DATA || provider == Provider.UNDER_STATS
                || provider == Provider.FOOTBALL_DATA_UK;
    }

    private void mergeFixtureField(Match match, Provider provider, String field, Object incoming,
                                   boolean currentPresent, Consumer<Object> setter) {
        if (incoming == null) return;
        mergeService.merge(match, provider, Dataset.FIXTURE, field, currentPresent, null,
                () -> setter.accept(incoming));
    }

    private static boolean statusTransitionAllowed(MatchStatus current, MatchStatus incoming) {
        if (current == null) return true;
        if (current == MatchStatus.FINISHED && incoming != MatchStatus.FINISHED) return false;
        return current != MatchStatus.LIVE || incoming != MatchStatus.SCHEDULED;
    }

    private static boolean hasText(String value) { return value != null && !value.isBlank(); }

    private static MatchStatus toMatchStatus(String status) {
        if (status == null || status.isBlank()) return null;
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "NS", "TBD", "SCHEDULED", "TIMED" -> MatchStatus.SCHEDULED;
            case "1H", "HT", "2H", "ET", "P", "LIVE", "BT", "IN_PLAY", "PAUSED" -> MatchStatus.LIVE;
            case "FT", "AET", "PEN", "FINISHED" -> MatchStatus.FINISHED;
            case "PST", "SUSP", "INT", "POSTPONED", "SUSPENDED" -> MatchStatus.POSTPONED;
            case "CANC", "ABD", "WO", "CANCELLED" -> MatchStatus.CANCELLED;
            default -> null;
        };
    }
}
