package com.mmo.module.fb.content.repository;

import com.mmo.module.fb.content.entity.ContentDraftVersionEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContentDraftVersionRepository extends JpaRepository<ContentDraftVersionEntity, Long> {
    List<ContentDraftVersionEntity> findByDraftIdOrderByVersionNumberDesc(Long draftId);
}