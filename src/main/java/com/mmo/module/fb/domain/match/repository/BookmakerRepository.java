package com.mmo.module.fb.domain.match.repository;

import com.mmo.module.fb.domain.match.entity.Bookmaker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

@Repository
public interface BookmakerRepository extends JpaRepository<Bookmaker, Long> {
    Optional<Bookmaker> findByName(String name);
    List<Bookmaker> findAllByNameIn(Collection<String> names);
}
