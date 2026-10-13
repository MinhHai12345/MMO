package com.mmo.module.fb.domain.entity;

import com.mmo.core.entity.AbstractEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.Lob;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "fb_social_posts")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SocialPost extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id")
    private Match match;

    @Column(nullable = false)
    private String platform;

    @Column(name = "post_id")
    private String postId;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(columnDefinition = "JSON")
    private String mediaUrls;

    @Column(name = "post_status")
    private String postStatus = "DRAFT";

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(columnDefinition = "JSON")
    private String engagementStats;

    @OneToMany(mappedBy = "socialPost", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ContentQueue> queue = new ArrayList<>();
}
