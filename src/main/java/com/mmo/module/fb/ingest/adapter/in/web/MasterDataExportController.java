package com.mmo.module.fb.ingest.adapter.in.web;

import com.mmo.module.fb.ingest.application.model.masterdata.MasterDataFile;
import com.mmo.module.fb.ingest.application.service.masterdata.MasterDataExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@RestController
@RequestMapping("/api/fb/master-data")
@RequiredArgsConstructor
public class MasterDataExportController {

    private final MasterDataExportService exportService;

    @Value("${app.ingestion.operations.api-key:}")
    private String configuredApiKey;

    @GetMapping(value = "/export", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> export(
            @RequestHeader(name = "X-Ingestion-Admin-Key", required = false) String suppliedApiKey) {
        if (configuredApiKey == null || configuredApiKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "Master data export is disabled: configure app.ingestion.operations.api-key."));
        }
        if (!constantTimeEquals(configuredApiKey, suppliedApiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid ingestion admin key."));
        }

        MasterDataFile masterData = exportService.export();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"football-master-data.json\"")
                .body(masterData);
    }

    private boolean constantTimeEquals(String expected, String supplied) {
        return supplied != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }
}
