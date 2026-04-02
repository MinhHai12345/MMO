package com.mmo.module.fb.repository;

import com.mmo.module.fb.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {
    Team findBySofaScoreId(Long sofaScoreId);

    List<Team> findBySofaScoreIdIn(Set<Long> sofaScoreIds);
}
