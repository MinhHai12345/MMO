package com.mmo.module.fb.ingest.provider.sofascore.dto;

import java.util.List;
import java.util.Map;

public record SofaDailyMatchWrapper(
        List<SofaMatchesData.SofaEventDTO> events,
        Map<Long, SofaOddsData.MatchOddDetailDTO> oddsMap
) {
    public List<SofaMatchesData.SofaEventDTO> getEvents() {
        return events;
    }

    public Map<Long, SofaOddsData.MatchOddDetailDTO> getOddsMap() {
        return oddsMap;
    }
}
