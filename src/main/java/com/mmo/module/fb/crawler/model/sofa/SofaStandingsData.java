package com.mmo.module.fb.crawler.model.sofa;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SofaStandingsData {
    @JsonProperty("standings")
    private List<StandingGroupDTO> standings;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StandingGroupDTO {
        private Long id;
        private String type;
        private String name;
        private List<StandingRowDTO> rows;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StandingRowDTO {
        private Integer position;
        private Integer matches;
        private Integer wins;
        private Integer draws;
        private Integer losses;
        private Integer points;
        private Integer scoresFor;
        private Integer scoresAgainst;
        private String scoreDiffFormatted;

        private TeamDTO team;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TeamDTO {
        private Long id;
        private String name;
        private String slug;
        private String shortName;
        private String nameCode;
        private Long userCount;

        private CountryDTO country;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CountryDTO {
        private String alpha2;
        private String alpha3;
        private String name;
    }
}
