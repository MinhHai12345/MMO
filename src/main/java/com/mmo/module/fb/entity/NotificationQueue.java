package com.mmo.module.fb.entity;

import com.mmo.entity.AbstractEntity;
import com.mmo.module.fb.enums.PublishStatus;
import com.mmo.module.fb.enums.TargetChannel;
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
@Table(name = "fb_notification_queue")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationQueue extends AbstractEntity {

    @Column
    @Enumerated(EnumType.STRING)
    private TargetChannel channel;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column
    @Enumerated(EnumType.STRING)
    private PublishStatus status;

    @Column
    private Integer retryCount;

    @Column
    private String errorMessage;

    @Column
    private Instant scheduledTime;

    @Column
    private Instant sentAt;
}
