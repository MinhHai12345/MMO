package com.mmo.module.fb.domain.repository;

import com.mmo.module.fb.domain.entity.ContentTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ContentTemplateRepository extends JpaRepository<ContentTemplate, Long> {
    
    Optional<ContentTemplate> findByPlatformAndTemplateType(String platform, String templateType);
}
