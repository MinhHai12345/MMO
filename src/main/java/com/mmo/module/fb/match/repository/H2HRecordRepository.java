package com.mmo.module.fb.match.repository;

import com.mmo.module.fb.match.entity.H2HRecord;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface H2HRecordRepository extends JpaRepository<H2HRecord, Long> {

    /** Directional lookup: first team is treated as home for the aggregate. */
    Optional<H2HRecord> findByHomeTeamIdAndAwayTeamId(Long homeTeamId, Long awayTeamId);

    @EntityGraph(attributePaths = {"homeTeam", "awayTeam"})
    @Query("""
            SELECT record FROM H2HRecord record
            WHERE (record.homeTeam.id = :teamA AND record.awayTeam.id = :teamB)
               OR (record.homeTeam.id = :teamB AND record.awayTeam.id = :teamA)
            """)
    List<H2HRecord> findBothDirections(@Param("teamA") Long teamA, @Param("teamB") Long teamB);

    @EntityGraph(attributePaths = {"homeTeam", "awayTeam"})
    @Query("SELECT record FROM H2HRecord record ORDER BY record.id ASC")
    List<H2HRecord> findAllWithTeams();
}
