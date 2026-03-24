package com.mmo.module.fb.repository;

import com.mmo.module.fb.entity.League;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeagueRepository extends JpaRepository<League, Long> {

    boolean existsByNameAndSeason(String name, String season);

    List<League> findBySeason(String season);

}
