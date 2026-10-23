package com.mmo.module.fb.publish.entity;

import com.mmo.core.entity.AbstractEntity;
import com.mmo.module.fb.match.entity.Match;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "fb_social_posts",
        uniqueConstraints = @UniqueConstraint(name = "uk_fb_social_post_platform_post", columnNames = {"platform", "post_id"}),
        indexes = {
                @Index(name = "idx_fb_social_post_match_status", columnList = "match_id, post_status"),
                @Index(name = "idx_fb_social_post_scheduled", columnList = "scheduled_at")
        })
@Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
public class SocialPost extends AbstractEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id")
    private Match match;
    @Column(nullable = false, length = 32)
    private String platform;
    @Column(name = "post_id", length = 255)
    private String postId;
    @Lob
    @Column(nullable = false)
    private String content;
    @Column(columnDefinition = "JSON")
    private String mediaUrls;
    @Column(name = "post_status")
    @Builder.Default
    private String postStatus = "DRAFT";
    @Column(name = "scheduled_at")
    private Instant scheduledAt;
    @Column(name = "published_at")
    private Instant publishedAt;
    @Column(columnDefinition = "JSON")
    private String engagementStats;
    @OneToMany(mappedBy = "socialPost", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ContentQueue> queue = new ArrayList<>();
}
