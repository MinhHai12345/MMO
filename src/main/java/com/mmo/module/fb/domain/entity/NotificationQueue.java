package com.mmo.module.fb.domain.entity;

import com.mmo.core.entity.AbstractEntity;
import com.mmo.module.fb.domain.enums.PublishStatus;
import com.mmo.module.fb.domain.enums.TargetChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
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
@Table(name = "fb_notification_queue",
        indexes = {
                @Index(name = "idx_fb_notification_status_schedule", columnList = "status, scheduled_time"),
                @Index(name = "idx_fb_notification_channel", columnList = "channel")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationQueue extends AbstractEntity {

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private TargetChannel channel;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private PublishStatus status;

    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "scheduled_time")
    private Instant scheduledTime;

    @Column(name = "sent_at")
    private Instant sentAt;
}
