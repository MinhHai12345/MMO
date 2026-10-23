package com.mmo.module.fb.publish.entity;

import com.mmo.core.entity.AbstractEntity;
import com.mmo.module.fb.publish.enums.TargetChannel;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "fb_social_post_logs", indexes = {
        @Index(name = "idx_fb_social_log_match_posted", columnList = "match_id, posted_at"),
        @Index(name = "idx_fb_social_log_external_post", columnList = "external_post_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SocialPostLog extends AbstractEntity {
    @Column
    private Long matchId;
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private TargetChannel channel;
    @Column
    private String externalPostId;
    @Column(name = "views_count")
    private Integer viewsCount;
    @Column(name = "likes_count")
    private Integer likesCount;
    @Column(name = "shares_count")
    private Integer sharesCount;
    @Column(name = "posted_at")
    private Instant postedAt;
}
