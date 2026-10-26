package com.mmo.module.fb.ingest.provider.sofascore.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Setter
public class SofaDailyMatchWrapper {
    private List<SofaMatchesData.SofaEventDTO> events;
    private Map<Long, SofaOddsData.MatchOddDetailDTO> oddsMap;
}
