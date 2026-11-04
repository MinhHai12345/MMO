package com.mmo.module.fb.match.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.match.entity.Match;
import com.mmo.module.fb.match.entity.MatchStatisticsSourceSnapshot;
import com.mmo.module.fb.match.enums.MatchProvider;
import com.mmo.module.fb.match.repository.MatchStatisticsSourceSnapshotRepository;
import com.mmo.module.fb.team.entity.Team;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Persists the latest provider payload representation independently for every provider and team side. */
@Service
@RequiredArgsConstructor
public class MatchStatisticsSnapshotPersistenceService {

    private final MatchStatisticsSourceSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void upsert(Match match, Team team, Provider provider, Object sourcePayload) {
        if (match == null || match.getId() == null || team == null || team.getId() == null
                || provider == null || sourcePayload == null) {
            return;
        }

        Long homeTeamId = match.getHomeTeam() == null ? null : match.getHomeTeam().getId();
        Long awayTeamId = match.getAwayTeam() == null ? null : match.getAwayTeam().getId();
        if (!team.getId().equals(homeTeamId) && !team.getId().equals(awayTeamId)) {
            throw new IllegalArgumentException("Statistics team must be the home or away team of the match");
        }

        final String json;
        try {
            json = objectMapper.writeValueAsString(sourcePayload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize match statistics source snapshot", exception);
        }

        MatchStatisticsSourceSnapshot snapshot = snapshotRepository
                .findByMatchIdAndTeamIdAndProvider(match.getId(), team.getId(), provider)
                .orElseGet(() -> MatchStatisticsSourceSnapshot.builder()
                        .match(match).team(team).provider(MatchProvider.from(provider)).build());
        snapshot.setSnapshotJson(json);
        snapshot.setFetchedAt(Instant.now());
        snapshotRepository.save(snapshot);
    }
}
