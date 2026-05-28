package com.mmo.module.fb.crawler.model.sofa;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor
public class SofaDailyMatchWrapper {
    private List<SofaMatchData.SofaEventDTO> events;
    private Map<String, SofaOddsData.MatchOddDetailDTO> odds;
}
