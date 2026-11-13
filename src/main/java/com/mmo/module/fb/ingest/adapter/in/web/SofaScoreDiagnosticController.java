package com.mmo.module.fb.ingest.adapter.in.web;

import com.mmo.module.fb.ingest.application.model.SofaScoreDiagnosticRequest;
import com.mmo.module.fb.ingest.application.model.SofaScoreDiagnosticResult;
import com.mmo.module.fb.ingest.application.port.in.SofaScoreDiagnosticUseCase;
import com.mmo.module.fb.ingest.application.port.in.SofaScoreLeagueLookupUseCase;
import com.mmo.module.fb.ingest.application.model.provider.ProviderLeague;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Protected, read-only operational endpoint for validating the SofaScore provider.
 * It fetches remote data but never starts ingestion or writes application data.
 */
@RestController
@RequestMapping("/api/fb/ingest/sofascore")
@RequiredArgsConstructor
public class SofaScoreDiagnosticController {

    private final SofaScoreDiagnosticUseCase diagnosticUseCase;
    private final SofaScoreLeagueLookupUseCase leagueLookupUseCase;

    @Value("${app.ingestion.operations.api-key:}")
    private String configuredApiKey;

    @PostMapping("/diagnostics")
    public ResponseEntity<?> diagnose(
            @RequestHeader(name = "X-Ingestion-Admin-Key", required = false) String suppliedApiKey,
            @RequestBody(required = false) SofaScoreDiagnosticRequest request) {
        if (configuredApiKey == null || configuredApiKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "SofaScore diagnostic API is disabled: configure app.ingestion.operations.api-key."));
        }
        if (!constantTimeEquals(configuredApiKey, suppliedApiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid ingestion admin key."));
        }
        try {
            SofaScoreDiagnosticResult result = diagnosticUseCase.diagnose(request);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
    }

    /**
     * Fetches one SofaScore tournament and its seasons only.
     * Unlike /diagnostics, this endpoint does not request teams, fixtures or lineups.
     */
    @GetMapping("/league/{tournamentId}")
    public ResponseEntity<?> lookupLeague(
            @RequestHeader(name = "X-Ingestion-Admin-Key", required = false) String suppliedApiKey,
            @PathVariable Long tournamentId) {
        if (configuredApiKey == null || configuredApiKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "SofaScore league lookup API is disabled: configure app.ingestion.operations.api-key."));
        }
        if (!constantTimeEquals(configuredApiKey, suppliedApiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid ingestion admin key."));
        }
        try {
            List<ProviderLeague> leagues = leagueLookupUseCase.fetchLeague(tournamentId);
            return ResponseEntity.ok(Map.of(
                    "provider", "SOFA_SCORE",
                    "tournamentId", tournamentId,
                    "status", leagues.isEmpty() ? "EMPTY" : "SUCCESS",
                    "itemCount", leagues.size(),
                    "leagues", leagues));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", exception.getMessage()));
        }
    }

    private boolean constantTimeEquals(String expected, String supplied) {
        if (supplied == null) return false;
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
    }
}
