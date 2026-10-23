package com.mmo.module.fb.content.repository;

import com.mmo.module.fb.content.entity.ContentTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ContentTemplateRepository extends JpaRepository<ContentTemplate, Long> {
    Optional<ContentTemplate> findByPlatformAndTemplateType(String platform, String templateType);
}
