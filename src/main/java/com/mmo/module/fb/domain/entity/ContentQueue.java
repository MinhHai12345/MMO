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

@Entity
@Table(name = "fb_content_queue",
        indexes = {
                @Index(name = "idx_fb_content_queue_status_priority", columnList = "queue_status, priority"),
                @Index(name = "idx_fb_content_queue_processed", columnList = "processed_at")
        })
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ContentQueue extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "social_post_id", nullable = false)
    private SocialPost socialPost;

    @Column(name = "queue_status")
    @Builder.Default
    private String queueStatus = "PENDING";

    @Builder.Default
    private Integer priority = 5;

    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "max_retries")
    @Builder.Default
    private Integer maxRetries = 3;

    @Lob
    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "processed_at")
    private Instant processedAt;
}
