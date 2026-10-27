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

    long countByEventTypeAndCurrencyAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
            String eventType, String currency, Instant from, Instant to);

    @Query("""
            select coalesce(sum(event.amount), 0)
            from ConversionEventEntity event
            where event.eventType = :eventType
              and event.currency = :currency
              and event.occurredAt >= :from
              and event.occurredAt < :to
            """)
    BigDecimal sumAmount(@Param("eventType") String eventType,
                         @Param("currency") String currency,
                         @Param("from") Instant from,
                         @Param("to") Instant to);
}
