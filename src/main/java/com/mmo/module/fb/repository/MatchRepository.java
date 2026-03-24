package com.mmo.module.fb.repository;

import com.mmo.module.fb.entity.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {
    List<Match> findAllByUnderStatMatchIdIn(Collection<String> underStatIds);
}
