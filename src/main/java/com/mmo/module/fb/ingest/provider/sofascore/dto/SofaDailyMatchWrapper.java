package com.mmo.module.fb.ingest.provider.sofascore.dto;

import java.util.List;
import java.util.Map;

/**
 * Combined daily fixtures and odds payload used by the SofaScore workflow.
 *
 * <p>Uses the native record accessors {@code events()} and {@code oddsMap()}.</p>
 */
public record SofaDailyMatchWrapper(
        List<SofaScoreResponseDTO.EventDTO> events,
        Map<Long, SofaScoreResponseDTO.MatchOddDetailDTO> oddsMap) {
}
