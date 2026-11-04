package com.mmo.module.fb.match.repository;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.match.entity.MatchStatisticsSourceSnapshot;
import com.mmo.module.fb.match.enums.MatchProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MatchStatisticsSourceSnapshotRepository
        extends JpaRepository<MatchStatisticsSourceSnapshot, Long> {

    Optional<MatchStatisticsSourceSnapshot> findByMatchIdAndTeamIdAndProvider(
            Long matchId, Long teamId, MatchProvider provider);

    List<MatchStatisticsSourceSnapshot> findAllByMatchId(Long matchId);

    List<MatchStatisticsSourceSnapshot> findAllByMatchIdAndTeamId(Long matchId, Long teamId);

    default Optional<MatchStatisticsSourceSnapshot> findByMatchIdAndTeamIdAndProvider(
            Long matchId, Long teamId, Provider provider) {
        return findByMatchIdAndTeamIdAndProvider(matchId, teamId, MatchProvider.from(provider));
    }
}
