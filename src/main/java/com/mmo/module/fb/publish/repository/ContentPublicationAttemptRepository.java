package com.mmo.module.fb.publish.repository;

import com.mmo.module.fb.publish.entity.ContentPublicationAttemptEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContentPublicationAttemptRepository extends JpaRepository<ContentPublicationAttemptEntity, Long> {
    List<ContentPublicationAttemptEntity> findByPublicationIdOrderByAttemptNumberDesc(Long publicationId);
}
