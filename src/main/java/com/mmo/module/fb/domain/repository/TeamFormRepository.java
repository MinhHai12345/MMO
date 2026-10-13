package com.mmo.module.fb.domain.repository;

import com.mmo.module.fb.domain.entity.TeamForm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TeamFormRepository extends JpaRepository<TeamForm, Long> {
    
    Optional<TeamForm> findByTeamIdAndSeasonIdAndFormType(Long teamId, Long seasonId, String formType);
}
