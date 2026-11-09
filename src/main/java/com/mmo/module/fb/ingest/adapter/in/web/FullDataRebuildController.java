package com.mmo.module.fb.ingest.adapter.in.web;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.entity.JobState;
import com.mmo.core.cronjob.repository.CronJobRepository;
import com.mmo.core.cronjob.service.CronJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Protected operational API for starting and inspecting the full data rebuild Quartz job.
 * The endpoint is disabled unless an operator API key is configured.
 */
@RestController
@RequestMapping("/api/fb/ingest/full-rebuild")
@RequiredArgsConstructor
public class FullDataRebuildController {

    private static final String JOB_NAME = "FullDataRebuildJob";
    private static final String JOB_GROUP = "FBJobGroup";

    private final CronJobRepository cronJobRepository;
    private final CronJobService cronJobService;

    @Value("${app.ingestion.operations.api-key:}")
    private String configuredApiKey;

    @PostMapping
    public ResponseEntity<?> start(
            @RequestHeader(name = "X-Ingestion-Admin-Key", required = false) String suppliedApiKey,
            @RequestBody(required = false) StartFullRebuildRequest request) {

        if (configuredApiKey == null || configuredApiKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "Full rebuild API is disabled: configure app.ingestion.operations.api-key."));
        }
        if (!constantTimeEquals(configuredApiKey, suppliedApiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid ingestion admin key."));
        }
        if (request == null || !Boolean.TRUE.equals(request.confirm())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Set confirm=true to acknowledge the full data rebuild."));
        }

        CronJob job = cronJobRepository.findByJobNameAndJobGroup(JOB_NAME, JOB_GROUP)
                .orElseThrow(() -> new IllegalStateException("FullDataRebuildJob has not been registered."));
        if (job.getJobStatus() == JobState.RUNNING) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "A full data rebuild is already running.", "job", job.getJobName()));
        }

        CronJob triggered = cronJobService.startJobNow(job.getId(), Map.of());
        return ResponseEntity.accepted().body(FullRebuildJobResponse.from(triggered));
    }

    @GetMapping
    public ResponseEntity<?> status(
            @RequestHeader(name = "X-Ingestion-Admin-Key", required = false) String suppliedApiKey) {
        if (configuredApiKey == null || configuredApiKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "Full rebuild API is disabled: configure app.ingestion.operations.api-key."));
        }
        if (!constantTimeEquals(configuredApiKey, suppliedApiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid ingestion admin key."));
        }

        CronJob job = cronJobRepository.findByJobNameAndJobGroup(JOB_NAME, JOB_GROUP)
                .orElseThrow(() -> new IllegalStateException("FullDataRebuildJob has not been registered."));
        return ResponseEntity.ok(FullRebuildJobResponse.from(job));
    }

    private boolean constantTimeEquals(String expected, String supplied) {
        if (supplied == null) return false;
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
    }

    public record StartFullRebuildRequest(Boolean confirm) {
    }

    public record FullRebuildJobResponse(
            String jobName,
            String status,
            String lastCompleteStatus,
            String lastExecutedTime,
            String description) {

        static FullRebuildJobResponse from(CronJob job) {
            return new FullRebuildJobResponse(
                    job.getJobName(),
                    job.getJobStatus() == null ? null : job.getJobStatus().name(),
                    job.getLastCompleteStatus() == null ? null : job.getLastCompleteStatus().name(),
                    job.getLastExecutedTime() == null ? null : job.getLastExecutedTime().toString(),
                    job.getDescription());
        }
    }
}
