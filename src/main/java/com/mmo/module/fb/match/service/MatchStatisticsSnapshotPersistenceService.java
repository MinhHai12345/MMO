package com.mmo.module.fb.match.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.match.entity.Match;
import com.mmo.module.fb.match.entity.MatchStatisticsSourceSnapshot;
import com.mmo.module.fb.match.repository.MatchStatisticsSourceSnapshotRepository;
import com.mmo.module.fb.team.entity.Team;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Persists the latest raw statistics payload independently for every provider and team side. */
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

        final String json;
        try {
            json = objectMapper.writeValueAsString(sourcePayload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize match statistics source snapshot", exception);
        }

        MatchStatisticsSourceSnapshot snapshot = snapshotRepository
                .findByMatchIdAndTeamIdAndProvider(match.getId(), team.getId(), provider)
                .orElseGet(() -> MatchStatisticsSourceSnapshot.builder()
                        .match(match).team(team).provider(provider).build());
        snapshot.setSnapshotJson(json);
        snapshot.setFetchedAt(Instant.now());
        snapshotRepository.save(snapshot);
    }
}
