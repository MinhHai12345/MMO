package com.mmo.module.fb.crawler.model.sofa;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SofaOddsData {

    private Map<String, MatchOddDetailDTO> odds;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MatchOddDetailDTO {
        private String id;
        private Integer marketId;
        private String marketName;
        private String marketGroup;
        private List<ChoiceDTO> choices;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChoiceDTO {
        private String name;
        private String fractionalValue;
        private Long sourceId;
    }
}