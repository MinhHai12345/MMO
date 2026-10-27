package com.mmo.module.fb.monetization.service;

import com.mmo.module.fb.monetization.entity.ConversionEventEntity;
import com.mmo.module.fb.monetization.repository.ConversionEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ConversionEventService {
    private static final Set<String> EVENT_TYPES = Set.of(
            "CLICK", "LEAD", "SUBSCRIPTION_CONFIRMED", "PAYMENT_CONFIRMED", "REFUND_CONFIRMED");
    private static final int MAX_KEY_LENGTH = 128;

    private final ConversionEventRepository repository;

    @Transactional
    public ConversionEventEntity record(RecordCommand command) {
        validate(command);
        String key = command.idempotencyKey().strip();
        var byKey = repository.findByIdempotencyKey(key);
        if (byKey.isPresent()) {
            assertSameEvent(byKey.get(), command);
            return byKey.get();
        }
        var byExternalId = repository.findByEventSourceAndExternalEventId(
                command.eventSource().strip(), command.externalEventId().strip());
        if (byExternalId.isPresent()) {
            assertSameEvent(byExternalId.get(), command);
            return byExternalId.get();
        }

        ConversionEventEntity event = new ConversionEventEntity();
        event.setIdempotencyKey(key);
        event.setEventSource(command.eventSource().strip());
        event.setExternalEventId(command.externalEventId().strip());
        event.setEventType(command.eventType().strip().toUpperCase(Locale.ROOT));
        event.setPublicationId(command.publicationId());
        event.setSubscriberId(command.subscriberId());
        event.setCampaignCode(blankToNull(command.campaignCode()));
        event.setAmount(command.amount());
        event.setCurrency(command.currency() == null ? null : command.currency().strip().toUpperCase(Locale.ROOT));
        event.setOccurredAt(command.occurredAt() == null ? Instant.now() : command.occurredAt());
        event.setRecordedAt(Instant.now());

        try {
            return repository.saveAndFlush(event);
        } catch (DataIntegrityViolationException duplicateRace) {
            // The current transaction may already be rollback-only. Let the caller retry;
            // the next request will resolve to the committed idempotent event if one won the race.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Duplicate event was recorded concurrently; retry with the same Idempotency-Key",
                    duplicateRace);
        }
    }

    @Transactional(readOnly = true)
    public RevenueReport revenue(Instant from, Instant to, String currency, String campaignCode) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be before to");
        }
        String normalizedCurrency = currency == null ? "" : currency.strip().toUpperCase(Locale.ROOT);
        if (!normalizedCurrency.matches("[A-Z]{3}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "currency must be a 3-letter ISO code");
        }

        String normalizedCampaign = campaignCode == null || campaignCode.isBlank() ? null : campaignCode.strip();
        BigDecimal payments = zero(repository.sumAmount("PAYMENT_CONFIRMED", normalizedCurrency, from, to, normalizedCampaign));
        BigDecimal refunds = zero(repository.sumAmount("REFUND_CONFIRMED", normalizedCurrency, from, to, normalizedCampaign));
        return new RevenueReport(from, to, normalizedCurrency, normalizedCampaign,
                repository.countEvents("CLICK", from, to, normalizedCampaign),
                repository.countEvents("LEAD", from, to, normalizedCampaign),
                repository.countEvents("SUBSCRIPTION_CONFIRMED", from, to, normalizedCampaign),
                repository.countEvents("PAYMENT_CONFIRMED", from, to, normalizedCampaign),
                repository.countEvents("REFUND_CONFIRMED", from, to, normalizedCampaign),
                payments, refunds, payments.subtract(refunds));
    }

    private static void validate(RecordCommand command) {
        if (command == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event payload is required");
        if (blank(command.idempotencyKey()) || command.idempotencyKey().strip().length() > MAX_KEY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key is required (max 128 characters)");
        }
        if (blank(command.eventSource()) || command.eventSource().strip().length() > 64
                || blank(command.externalEventId()) || command.externalEventId().strip().length() > 160) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "eventSource (max 64) and externalEventId (max 160) are required");
        }
        if (command.campaignCode() != null && command.campaignCode().strip().length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "campaignCode must not exceed 100 characters");
        }
        String type = command.eventType() == null ? "" : command.eventType().strip().toUpperCase(Locale.ROOT);
        if (!EVENT_TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported eventType");
        }
        boolean financial = "PAYMENT_CONFIRMED".equals(type) || "REFUND_CONFIRMED".equals(type);
        if (financial && (command.amount() == null || command.amount().signum() <= 0
                || command.currency() == null || !command.currency().strip().toUpperCase(Locale.ROOT).matches("[A-Z]{3}"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Confirmed payment/refund events require positive amount and a 3-letter currency");
        }
        if (!financial && command.amount() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only confirmed payment/refund events may carry an amount");
        }
    }

    private static void assertSameEvent(ConversionEventEntity existing, RecordCommand command) {
        String requestedType = command.eventType().strip().toUpperCase(Locale.ROOT);
        if (!existing.getEventSource().equals(command.eventSource().strip())
                || !existing.getExternalEventId().equals(command.externalEventId().strip())
                || !existing.getEventType().equals(requestedType)
                || !java.util.Objects.equals(existing.getPublicationId(), command.publicationId())
                || !java.util.Objects.equals(existing.getSubscriberId(), command.subscriberId())
                || !java.util.Objects.equals(existing.getAmount(), command.amount())
                || !java.util.Objects.equals(existing.getCurrency(),
                    command.currency() == null ? null : command.currency().strip().toUpperCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Idempotency key or external event ID was reused with different event data");
        }
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }

    public record RecordCommand(String idempotencyKey, String eventSource, String externalEventId,
                                String eventType, Long publicationId, Long subscriberId, String campaignCode,
                                BigDecimal amount, String currency, Instant occurredAt) {}
    public record RevenueReport(Instant from, Instant to, String currency, String campaignCode, long clicks, long leads,
                                long subscriptions, long confirmedPayments, long confirmedRefunds,
                                BigDecimal grossRevenue, BigDecimal refunds, BigDecimal netRevenue) {}
}
