package com.mmo.module.fb.analytics.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContentDraftVersionRepository extends JpaRepository<ContentDraftVersionEntity, Long> {
    List<ContentDraftVersionEntity> findByDraftIdOrderByVersionNumberDesc(Long draftId);
}