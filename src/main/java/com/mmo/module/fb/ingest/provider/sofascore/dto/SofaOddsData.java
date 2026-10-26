package com.mmo.module.fb.ingest.provider.sofascore.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SofaOddsData(Map<Long, MatchOddDetailDTO> odds) {
    public Map<Long, MatchOddDetailDTO> getOdds() { return odds; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MatchOddDetailDTO(String id, Integer marketId, String marketName,
                                    String marketGroup, List<ChoiceDTO> choices) {
        public String getId() { return id; }
        public Integer getMarketId() { return marketId; }
        public String getMarketName() { return marketName; }
        public String getMarketGroup() { return marketGroup; }
        public List<ChoiceDTO> getChoices() { return choices; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChoiceDTO(String name, String fractionalValue, Long sourceId) {
        public String getName() { return name; }
        public String getFractionalValue() { return fractionalValue; }
        public Long getSourceId() { return sourceId; }
    }
}
