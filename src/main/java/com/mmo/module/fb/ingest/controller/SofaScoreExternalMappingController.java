package com.mmo.module.fb.ingest.controller;

import com.mmo.module.fb.ingest.service.impl.SofaScoreExternalMappingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Starts an explicit, one-shot provider-ID mapping run. No scheduler/job is created.
 */
@RestController
@RequestMapping("/api/fb/ingest/external-mappings")
@RequiredArgsConstructor
public class SofaScoreExternalMappingController {

    private final SofaScoreExternalMappingService mappingService;

    @PostMapping("/sofascore")
    public SofaScoreExternalMappingService.MappingResult mapSofaScoreIds() {
        return mappingService.mapExistingCoreData();
    }
}
