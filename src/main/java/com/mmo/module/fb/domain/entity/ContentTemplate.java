package com.mmo.module.fb.domain.entity;

import com.mmo.core.entity.AbstractEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "fb_content_templates")
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

    @Column(name = "template_type", nullable = false)
    private String templateType;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(columnDefinition = "JSON")
    private String variables;

    @Column(name = "is_active")
    private Boolean isActive = true;
}
