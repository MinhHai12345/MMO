package com.mmo.module.fb.ingest.service.impl;

import com.mmo.module.fb.match.entity.Match;
import com.mmo.module.fb.league.entity.Season;
import com.mmo.module.fb.team.entity.Team;
import com.mmo.module.fb.match.enums.MatchStatus;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.match.repository.MatchExternalIdRepository;
import com.mmo.module.fb.match.repository.MatchRepository;
import com.mmo.module.fb.league.repository.SeasonRepository;
import com.mmo.module.fb.team.repository.TeamRepository;
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

        Match match = matchExternalIdRepository.findByProviderAndExternalId(provider, fixture.externalId())
                .map(com.mmo.module.fb.match.entity.MatchExternalId::getMatch)
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

        if (match == null && provider != Provider.FOOTBALL_DATA) {
            log.debug("Skipping SofaScore fixture {} because no unique existing canonical fixture matched; SofaScore mapping sync does not create core entities.",
                    fixture.externalId());
            return false;
        }

        if (match == null && fixture.kickoff() == null) {
            log.warn("Skipping new fixture {} because kickoff time is missing", fixture.externalId());
            return false;
        }
        if (match == null) {
            match = new Match();
        }

        if (provider != Provider.FOOTBALL_DATA) {
            // SofaScore is a secondary identity/enrichment source. Preserve canonical
            // football-data fields and only attach its provider-specific ID.
            externalIdMappingService.registerMatch(match, provider, fixture.externalId());
            return true;
        }

        match.setSeason(season);
        match.setHomeTeam(home);
        match.setAwayTeam(away);
        if (fixture.kickoff() != null) match.setMatchTime(fixture.kickoff());
        if (fixture.round() != null && !fixture.round().isBlank()) match.setRoundInfo(fixture.round());
        if (fixture.venue() != null && !fixture.venue().isBlank()) match.setVenue(fixture.venue());
        if (fixture.referee() != null && !fixture.referee().isBlank()) match.setReferee(fixture.referee());

        MatchStatus incomingStatus = toMatchStatus(fixture.status());
        if (incomingStatus != null
                && (match.getStatus() != MatchStatus.FINISHED || incomingStatus == MatchStatus.FINISHED)
                && (match.getStatus() != MatchStatus.LIVE || incomingStatus != MatchStatus.SCHEDULED)) {
            match.setStatus(incomingStatus);
        } else if (match.getStatus() == null) {
            match.setStatus(MatchStatus.SCHEDULED);
        }
        if (fixture.homeScore() != null) match.setHomeScore(fixture.homeScore());
        if (fixture.awayScore() != null) match.setAwayScore(fixture.awayScore());
        match = matchRepository.save(match);

        externalIdMappingService.registerMatch(match, provider, fixture.externalId());
        return true;
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
}
