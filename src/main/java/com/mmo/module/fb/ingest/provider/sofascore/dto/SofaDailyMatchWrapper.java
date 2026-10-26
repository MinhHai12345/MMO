package com.mmo.module.fb.ingest.provider.sofascore.dto;

import java.util.List;
import java.util.Map;

/** Combined daily fixtures and odds payload used by the SofaScore workflow. */
public record SofaDailyMatchWrapper(List<SofaScoreResponseDTO.EventDTO> events,
                                    Map<Long, SofaScoreResponseDTO.MatchOddDetailDTO> oddsMap) {
    public List<SofaScoreResponseDTO.EventDTO> getEvents(){return events;}
    public Map<Long, SofaScoreResponseDTO.MatchOddDetailDTO> getOddsMap(){return oddsMap;}
}
