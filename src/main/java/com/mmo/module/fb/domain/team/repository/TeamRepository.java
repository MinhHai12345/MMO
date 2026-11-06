package com.mmo.module.fb.domain.team.repository;

import com.mmo.module.fb.domain.team.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {

    /** Locks canonical teams in deterministic order before fixture identity reconciliation. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Team t where t.id in :ids order by t.id")
    List<Team> lockAllByIds(@Param("ids") Collection<Long> ids);

    Optional<Team> findByName(String name);
    List<Team> findAllByNameIgnoreCase(String name);
}
