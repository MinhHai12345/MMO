package com.mmo.module.fb.ingest.adapter.in.web;

import com.mmo.module.fb.ingest.application.model.masterdata.MasterDataInitializationReport;
import com.mmo.module.fb.ingest.application.service.masterdata.MasterDataInitializationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@RestController
@RequestMapping("/api/fb/master-data/initialize")
@RequiredArgsConstructor
public class MasterDataInitializationController {

    private final MasterDataInitializationService initializationService;

    @Value("${app.ingestion.operations.api-key:}")
    private String configuredApiKey;

    @PostMapping
    public ResponseEntity<?> initialize(
            @RequestHeader(name = "X-Ingestion-Admin-Key", required = false) String suppliedApiKey,
            @RequestBody(required = false) InitializationRequest request) {
        if (configuredApiKey == null || configuredApiKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "Master data initialization is disabled: configure app.ingestion.operations.api-key."));
        }
        if (!constantTimeEquals(configuredApiKey, suppliedApiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid ingestion admin key."));
        }
        String mode = request == null || request.mode() == null ? "DRY_RUN" : request.mode().trim().toUpperCase();
        if ("DRY_RUN".equals(mode)) {
            MasterDataInitializationReport report = initializationService.preview();
            return report.valid() ? ResponseEntity.ok(report) : ResponseEntity.badRequest().body(report);
        }
        if (!"APPLY".equals(mode)) {
            return ResponseEntity.badRequest().body(Map.of("message", "mode must be DRY_RUN or APPLY."));
        }
        if (request == null || !Boolean.TRUE.equals(request.confirm())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Set confirm=true to apply master data."));
        }
        return ResponseEntity.ok(initializationService.apply());
    }

    private boolean constantTimeEquals(String expected, String supplied) {
        return supplied != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }

    public record InitializationRequest(String mode, Boolean confirm) { }
}
