package com.mmo.module.fb.monetization.entity;

import com.mmo.core.entity.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable observation ledger for funnel events and verified financial events.
 * This table stores observed facts; it must never infer revenue from clicks or leads.
 */
@Entity
@Table(name = "fb_conversion_events",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_fb_conversion_idempotency", columnNames = "idempotency_key"),
                @UniqueConstraint(name = "uk_fb_conversion_source_external", columnNames = {"event_source", "external_event_id"})
        },
        indexes = {
                @Index(name = "idx_fb_conversion_time_type_currency", columnList = "occurred_at, event_type, currency"),
                @Index(name = "idx_fb_conversion_publication_time", columnList = "publication_id, occurred_at"),
                @Index(name = "idx_fb_conversion_subscriber_time", columnList = "subscriber_id, occurred_at")
        })
@Getter
@Setter
@NoArgsConstructor
public class ConversionEventEntity extends AbstractEntity {
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "event_source", nullable = false, length = 64)
    private String eventSource;

    @Column(name = "external_event_id", nullable = false, length = 160)
    private String externalEventId;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(name = "publication_id")
    private Long publicationId;

    @Column(name = "subscriber_id")
    private Long subscriberId;

    @Column(name = "campaign_code", length = 100)
    private String campaignCode;

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(length = 3)
    private String currency;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt = Instant.now();
}
