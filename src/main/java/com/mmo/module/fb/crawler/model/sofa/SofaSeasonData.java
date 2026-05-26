package com.mmo.module.fb.crawler.model.sofa;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SofaSeasonData {
    @JsonProperty("seasons")
    private List<SeasonDTO> seasons;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SeasonDTO {
        @JsonProperty("id")
        private Long id;
        private String name;
        private String year;
        private boolean isCurrent;
    }
}
