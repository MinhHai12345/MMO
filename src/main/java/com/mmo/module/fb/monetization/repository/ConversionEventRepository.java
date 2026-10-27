package com.mmo.module.fb.monetization.repository;

import com.mmo.module.fb.monetization.entity.ConversionEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public interface ConversionEventRepository extends JpaRepository<ConversionEventEntity, Long> {
    Optional<ConversionEventEntity> findByIdempotencyKey(String idempotencyKey);
    Optional<ConversionEventEntity> findByEventSourceAndExternalEventId(String eventSource, String externalEventId);

    @Query("""
            select count(event)
            from ConversionEventEntity event
            where event.eventType = :eventType
              and event.occurredAt >= :from
              and event.occurredAt < :to
              and (:campaignCode is null or event.campaignCode = :campaignCode)
            """)
    long countEvents(@Param("eventType") String eventType,
                     @Param("from") Instant from,
                     @Param("to") Instant to,
                     @Param("campaignCode") String campaignCode);

    @Query("""
            select sum(event.amount)
            from ConversionEventEntity event
            where event.eventType = :eventType
              and event.currency = :currency
              and event.occurredAt >= :from
              and event.occurredAt < :to
              and (:campaignCode is null or event.campaignCode = :campaignCode)
            """)
    BigDecimal sumAmount(@Param("eventType") String eventType,
                         @Param("currency") String currency,
                         @Param("from") Instant from,
                         @Param("to") Instant to,
                         @Param("campaignCode") String campaignCode);
}
