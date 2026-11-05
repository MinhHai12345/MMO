package com.mmo.module.fb.domain.match.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmo.module.fb.domain.match.enums.MatchProvider;
import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.match.entity.MatchStatisticsSourceSnapshot;
import com.mmo.module.fb.domain.match.repository.MatchStatisticsSourceSnapshotRepository;
import com.mmo.module.fb.domain.team.entity.Team;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Appends immutable provider payload snapshots; canonical statistics are merged separately. */
@Service
@RequiredArgsConstructor
public class MatchStatisticsSnapshotPersistenceService {

    private final MatchStatisticsSourceSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void upsert(Match match, Team team, MatchProvider provider, Object sourcePayload) {
        upsert(match, team, provider, sourcePayload, null);
    }

    @Transactional
    public void upsert(Match match, Team team, MatchProvider provider,
                       Object sourcePayload, Instant sourceUpdatedAt) {
        if (match == null || match.getId() == null || team == null || team.getId() == null
                || provider == null || sourcePayload == null) return;

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

        snapshotRepository.save(MatchStatisticsSourceSnapshot.builder()
                .match(match).team(team).provider(provider).snapshotJson(json)
                .sourceUpdatedAt(sourceUpdatedAt).fetchedAt(Instant.now()).build());
    }
}
