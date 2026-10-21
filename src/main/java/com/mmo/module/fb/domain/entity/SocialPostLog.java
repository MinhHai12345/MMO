package com.mmo.module.fb.domain.entity;

import com.mmo.core.entity.AbstractEntity;
import com.mmo.module.fb.domain.enums.TargetChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "fb_social_post_logs",
        indexes = {
                @Index(name = "idx_fb_social_log_match_posted", columnList = "match_id, posted_at"),
                @Index(name = "idx_fb_social_log_external_post", columnList = "external_post_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocialPostLog extends AbstractEntity {

    @Column
    private Long matchId;

    @Column
    @Enumerated(EnumType.STRING)
    private TargetChannel channel;

    @Column
    private String externalPostId;

    @Column
    private Integer viewsCount;

    @Column
    private Integer likesCount;

    @Column
    private Integer sharesCount;

    @Column
    private Instant postedAt;
}
