package com.mmo.module.fb.domain.repository;

import com.mmo.module.fb.domain.entity.Bookmaker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookmakerRepository extends JpaRepository<Bookmaker, Long> {
}
