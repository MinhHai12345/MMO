package com.mmo.module.fb.domain.entity;

import com.mmo.core.entity.AbstractEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "fb_content_templates",
        indexes = {
                @Index(name = "idx_fb_template_platform_type", columnList = "platform, template_type"),
                @Index(name = "idx_fb_template_active", columnList = "is_active")
        })
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ContentTemplate extends AbstractEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String platform;

    @Column(name = "template_type", nullable = false, length = 64)
    private String templateType;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(columnDefinition = "JSON")
    private String variables;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;
}
