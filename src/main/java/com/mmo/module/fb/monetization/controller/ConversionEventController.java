package com.mmo.module.fb.monetization.controller;

import com.mmo.module.fb.monetization.entity.ConversionEventEntity;
import com.mmo.module.fb.monetization.service.ConversionEventService;
import com.mmo.module.fb.monetization.service.ConversionEventService.RecordCommand;
import com.mmo.module.fb.monetization.service.ConversionEventService.RevenueReport;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

@RestController
@RequestMapping("/api/fb/monetization/events")
@RequiredArgsConstructor
public class ConversionEventController {
    private final ConversionEventService eventService;

    @Value("${app.monetization.events.api-key:}")
    private String configuredApiKey;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConversionEventEntity record(
            @RequestHeader(value = "X-Monetization-Event-Key", required = false) String suppliedKey,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody RecordCommand command) {
        authorize(suppliedKey);
        String headerKey = idempotencyKey == null || idempotencyKey.isBlank()
                ? command.idempotencyKey() : idempotencyKey;
        RecordCommand normalized = new RecordCommand(headerKey, command.eventSource(), command.externalEventId(),
                command.eventType(), command.publicationId(), command.subscriberId(), command.campaignCode(),
                command.amount(), command.currency(), command.occurredAt());
        return eventService.record(normalized);
    }

    @GetMapping("/revenue")
    public RevenueReport revenue(
            @RequestHeader(value = "X-Monetization-Event-Key", required = false) String suppliedKey,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam String currency) {
        authorize(suppliedKey);
        return eventService.revenue(from, to, currency);
    }

    private void authorize(String suppliedKey) {
        if (configuredApiKey == null || configuredApiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Monetization event ingestion is disabled until FB_MONETIZATION_EVENTS_API_KEY is configured");
        }
        if (suppliedKey == null || !MessageDigest.isEqual(
                configuredApiKey.getBytes(StandardCharsets.UTF_8),
                suppliedKey.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid monetization event credentials");
        }
    }
}
