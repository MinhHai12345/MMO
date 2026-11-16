package com.mmo.module.fb.ingest.adapter.in.web;

import com.mmo.module.fb.ingest.application.model.masterdata.ProviderMasterDataDiscoveryReport;
import com.mmo.module.fb.ingest.application.service.masterdata.ProviderMasterDataDiscoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@RestController
@RequestMapping("/api/fb/master-data/discovery")
@RequiredArgsConstructor
public class ProviderMasterDataDiscoveryController {

    private final ProviderMasterDataDiscoveryService discoveryService;

    @Value("${app.ingestion.operations.api-key:}")
    private String configuredApiKey;

    @PostMapping
    public ResponseEntity<?> discover(
            @RequestHeader(name = "X-Ingestion-Admin-Key", required = false) String suppliedApiKey,
            @RequestBody(required = false) ProviderMasterDataDiscoveryService.DiscoveryRequest request) {
        if (configuredApiKey == null || configuredApiKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "Provider discovery is disabled: configure app.ingestion.operations.api-key."));
        }
        if (!constantTimeEquals(configuredApiKey, suppliedApiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid ingestion admin key."));
        }
        ProviderMasterDataDiscoveryReport report = discoveryService.discover(request);
        return ResponseEntity.ok(report);
    }

    private boolean constantTimeEquals(String expected, String supplied) {
        return supplied != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }
}
