package com.mmo.module.fb.monetization.controller;

import com.mmo.module.fb.monetization.service.ConversionEventService;
import com.mmo.module.fb.monetization.service.ConversionEventService.RecordCommand;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/fb/monetization/stripe")
@RequiredArgsConstructor
public class StripeRevenueWebhookController {
    private static final List<String> ZERO_DECIMAL_CURRENCIES = List.of(
            "BIF", "CLP", "DJF", "GNF", "JPY", "KMF", "KRW", "MGA", "PYG", "RWF",
            "VND", "VUV", "XAF", "XOF", "XPF");
    private static final List<String> THREE_DECIMAL_CURRENCIES = List.of("BHD", "JOD", "KWD", "OMR", "TND");

    private final ConversionEventService eventService;

    @Value("${app.stripe.api.webhook.secret:}")
    private String webhookSecret;

    @PostMapping("/webhook")
    public WebhookResponse receive(
            @RequestHeader(value = "Stripe-Signature", required = false) String signature,
            @RequestBody String payload) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Stripe webhook is disabled until STRIPE_WEBHOOK_SECRET is configured");
        }
        if (signature == null || signature.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stripe-Signature header is required");
        }

        final Event event;
        try {
            event = Webhook.constructEvent(payload, signature, webhookSecret);
        } catch (SignatureVerificationException | RuntimeException invalidWebhook) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Stripe webhook signature or payload");
        }

        String type = event.getType();
        StripeObject object = event.getDataObjectDeserializer().getObject()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Stripe event payload could not be deserialized for type " + type));

        if ("payment_intent.succeeded".equals(type) && object instanceof PaymentIntent intent) {
            Long amountReceived = intent.getAmountReceived();
            if (amountReceived == null || amountReceived <= 0) {
                return new WebhookResponse(true, false, event.getId(), "No positive amount_received to record");
            }
            String currency = normalizeCurrency(intent.getCurrency());
            Map<String, String> metadata = intent.getMetadata();
            eventService.record(new RecordCommand(event.getId(), "stripe", event.getId(),
                    "PAYMENT_CONFIRMED", longMetadata(metadata, "publication_id"),
                    longMetadata(metadata, "subscriber_id"), metadataValue(metadata, "campaign_code"),
                    fromMinorUnits(amountReceived, currency), currency, Instant.ofEpochSecond(event.getCreated())));
            return new WebhookResponse(true, true, event.getId(), "Confirmed payment recorded idempotently");
        }

        if ("invoice.paid".equals(type) && object instanceof Invoice invoice
                && "subscription_create".equals(invoice.getBillingReason())
                && invoice.getAmountPaid() != null && invoice.getAmountPaid() > 0) {
            Map<String, String> metadata = invoice.getMetadata();
            eventService.record(new RecordCommand(event.getId(), "stripe", event.getId(),
                    "SUBSCRIPTION_CONFIRMED", longMetadata(metadata, "publication_id"),
                    longMetadata(metadata, "subscriber_id"), metadataValue(metadata, "campaign_code"),
                    null, null, Instant.ofEpochSecond(event.getCreated())));
            return new WebhookResponse(true, true, event.getId(), "New paid subscription recorded idempotently");
        }

        if ("refund.created".equals(type) && object instanceof Refund refund) {
            Long amount = refund.getAmount();
            if (amount == null || amount <= 0) {
                return new WebhookResponse(true, false, event.getId(), "No positive refund amount to record");
            }
            String currency = normalizeCurrency(refund.getCurrency());
            Map<String, String> metadata = refund.getMetadata();
            eventService.record(new RecordCommand(event.getId(), "stripe", event.getId(),
                    "REFUND_CONFIRMED", longMetadata(metadata, "publication_id"),
                    longMetadata(metadata, "subscriber_id"), metadataValue(metadata, "campaign_code"),
                    fromMinorUnits(amount, currency), currency, Instant.ofEpochSecond(event.getCreated())));
            return new WebhookResponse(true, true, event.getId(), "Confirmed refund recorded idempotently");
        }

        return new WebhookResponse(true, false, event.getId(), "Event type is not part of the revenue ledger");
    }

    private static BigDecimal fromMinorUnits(long amount, String currency) {
        int exponent = ZERO_DECIMAL_CURRENCIES.contains(currency) ? 0
                : THREE_DECIMAL_CURRENCIES.contains(currency) ? 3 : 2;
        return BigDecimal.valueOf(amount).movePointLeft(exponent);
    }

    private static String normalizeCurrency(String currency) {
        if (currency == null || !currency.strip().matches("[A-Za-z]{3}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stripe event currency is invalid");
        }
        return currency.strip().toUpperCase(java.util.Locale.ROOT);
    }

    private static String metadataValue(Map<String, String> metadata, String key) {
        if (metadata == null) return null;
        String value = metadata.get(key);
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static Long longMetadata(Map<String, String> metadata, String key) {
        String value = metadataValue(metadata, key);
        if (value == null) return null;
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public record WebhookResponse(boolean received, boolean recorded, String eventId, String message) {}
}
