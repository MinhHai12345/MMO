package com.mmo.module.fb.ingest.repository;

import com.mmo.module.fb.ingest.entity.CrawlerHealth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CrawlerHealthRepository extends JpaRepository<CrawlerHealth, Long> {
}
