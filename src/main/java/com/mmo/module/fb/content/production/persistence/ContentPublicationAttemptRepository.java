package com.mmo.module.fb.content.production.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContentPublicationAttemptRepository extends JpaRepository<ContentPublicationAttemptEntity, Long> {
    List<ContentPublicationAttemptEntity> findByPublicationIdOrderByAttemptNumberDesc(Long publicationId);
}
