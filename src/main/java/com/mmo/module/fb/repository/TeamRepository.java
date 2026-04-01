package com.mmo.module.fb.repository;

import com.mmo.module.fb.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {
    Team findBySofaScoreId(Long sofaScoreId);
}
