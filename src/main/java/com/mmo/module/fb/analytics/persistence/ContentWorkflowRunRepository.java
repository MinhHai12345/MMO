package com.mmo.module.fb.analytics.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContentWorkflowRunRepository extends JpaRepository<ContentWorkflowRunEntity, Long> {
    List<ContentWorkflowRunEntity> findAllByOrderByIdDesc(Pageable pageable);
}