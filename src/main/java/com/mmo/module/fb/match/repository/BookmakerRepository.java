package com.mmo.module.fb.match.repository;

import com.mmo.module.fb.match.entity.Bookmaker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BookmakerRepository extends JpaRepository<Bookmaker, Long> {
    Optional<Bookmaker> findByName(String name);
}
