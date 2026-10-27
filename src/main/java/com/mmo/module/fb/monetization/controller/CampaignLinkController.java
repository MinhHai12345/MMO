package com.mmo.module.fb.monetization.controller;

import com.mmo.module.fb.monetization.entity.CampaignLinkEntity;
import com.mmo.module.fb.monetization.service.CampaignLinkService;
import com.mmo.module.fb.monetization.service.CampaignLinkService.CreateCampaignCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequiredArgsConstructor
public class CampaignLinkController {
    private final CampaignLinkService campaignLinkService;

    @Value("${app.monetization.events.api-key:}")
    private String configuredApiKey;

    @PostMapping("/api/fb/monetization/campaigns")
    @ResponseStatus(HttpStatus.CREATED)
    public CampaignLinkEntity create(
            @RequestHeader(value = "X-Monetization-Event-Key", required = false) String suppliedKey,
            @RequestBody CreateCampaignCommand command) {
        authorize(suppliedKey);
        return campaignLinkService.create(command);
    }

    @PatchMapping("/api/fb/monetization/campaigns/{campaignCode}")
    public CampaignLinkEntity setActive(
            @RequestHeader(value = "X-Monetization-Event-Key", required = false) String suppliedKey,
            @PathVariable String campaignCode,
            @RequestParam boolean active) {
        authorize(suppliedKey);
        return campaignLinkService.setActive(campaignCode, active);
    }

    @GetMapping("/r/{campaignCode}")
    public ResponseEntity<Void> redirect(@PathVariable String campaignCode) {
        URI destination = campaignLinkService.recordClickAndGetDestination(campaignCode);
        return ResponseEntity.status(HttpStatus.FOUND).location(destination).build();
    }

    private void authorize(String suppliedKey) {
        if (configuredApiKey == null || configuredApiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Campaign link management is disabled until FB_MONETIZATION_EVENTS_API_KEY is configured");
        }
        if (suppliedKey == null || !MessageDigest.isEqual(
                configuredApiKey.getBytes(StandardCharsets.UTF_8),
                suppliedKey.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid monetization event credentials");
        }
    }
}
