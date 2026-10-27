package com.mmo.module.fb.team.repository;

import com.mmo.module.fb.team.entity.TeamForm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamFormRepository extends JpaRepository<TeamForm, Long> {
    
    Optional<TeamForm> findByTeamIdAndSeasonIdAndFormType(Long teamId, Long seasonId, String formType);
    @Query("SELECT form FROM TeamForm form JOIN FETCH form.team WHERE form.season.id = :seasonId AND form.formType = :formType")
    List<TeamForm> findBySeasonIdAndFormType(@Param("seasonId") Long seasonId,
                                             @Param("formType") String formType);

}