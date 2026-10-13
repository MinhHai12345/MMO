package com.mmo.module.fb.domain.repository;

import com.mmo.module.fb.domain.entity.ContentQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContentQueueRepository extends JpaRepository<ContentQueue, Long> {
}
