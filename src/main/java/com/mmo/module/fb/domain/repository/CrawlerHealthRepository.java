package com.mmo.module.fb.domain.repository;

import com.mmo.module.fb.domain.entity.CrawlerHealth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CrawlerHealthRepository extends JpaRepository<CrawlerHealth, Long> {
}
