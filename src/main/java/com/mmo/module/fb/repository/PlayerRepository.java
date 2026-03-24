package com.mmo.module.fb.repository;

import com.mmo.module.fb.entity.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PlayerRepository extends JpaRepository<Player, Long> {
    List<Player> findAllByUnderStatIdIn(Collection<String> underStatIds);
}
