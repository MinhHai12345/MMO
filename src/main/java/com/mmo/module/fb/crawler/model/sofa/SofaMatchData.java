package com.mmo.module.fb.crawler.model.sofa;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SofaMatchData {
    @JsonProperty("events")
    private List<SofaEventDTO> events;

    @JsonProperty("events")
    private boolean hasNextPage;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SofaEventDTO {

        @JsonProperty("id")
        private String id;

        private String slug;
        private Long startTimestamp;
        private Boolean hasXg;

        private TournamentDTO tournament;
        private SeasonDTO season;
        private TeamDTO homeTeam;
        private TeamDTO awayTeam;
        private ScoreDetailDTO homeScore;
        private ScoreDetailDTO awayScore;
        private StatusDTO status;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TournamentDTO {
        private String name;
        private String slug;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SeasonDTO {
        private String name;
        private String year;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TeamDTO {
        private String name;
        private String slug;
        private String shortName;
        private String nameCode;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StatusDTO {
        private Integer code;
        private String description;
        private String type;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScoreDetailDTO {
        private Integer current;
        private Integer display;
        private Integer period1;
        private Integer period2;
    }
}
