package com.mmo.module.fb.player.repository;

import com.mmo.module.fb.player.entity.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PlayerRepository extends JpaRepository<Player, Long> {
    List<Player> findAllByNameIgnoreCaseAndTeam_Id(String name, Long teamId);
}
