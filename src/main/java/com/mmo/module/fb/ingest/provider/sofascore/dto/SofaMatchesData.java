package com.mmo.module.fb.ingest.provider.sofascore.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SofaMatchesData(
        @JsonProperty("events") List<SofaEventDTO> events,
        @JsonProperty("hasNextPage") boolean hasNextPage
) {
    public List<SofaEventDTO> getEvents() { return events; }
    public boolean isHasNextPage() { return hasNextPage; }
    public boolean getHasNextPage() { return hasNextPage; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SofaEventDTO(@JsonProperty("id") Long id, String slug, Long startTimestamp,
                               Boolean hasXg, TournamentDTO tournament, SeasonDTO season,
                               TeamDTO homeTeam, TeamDTO awayTeam, ScoreDetailDTO homeScore,
                               ScoreDetailDTO awayScore, StatusDTO status, RoundInfo roundInfo) {
        public Long getId() { return id; }
        public String getSlug() { return slug; }
        public Long getStartTimestamp() { return startTimestamp; }
        public Boolean getHasXg() { return hasXg; }
        public TournamentDTO getTournament() { return tournament; }
        public SeasonDTO getSeason() { return season; }
        public TeamDTO getHomeTeam() { return homeTeam; }
        public TeamDTO getAwayTeam() { return awayTeam; }
        public ScoreDetailDTO getHomeScore() { return homeScore; }
        public ScoreDetailDTO getAwayScore() { return awayScore; }
        public StatusDTO getStatus() { return status; }
        public RoundInfo getRoundInfo() { return roundInfo; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TournamentDTO(String name, String slug) {
        public String getName() { return name; }
        public String getSlug() { return slug; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeasonDTO(String name, String year) {
        public String getName() { return name; }
        public String getYear() { return year; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TeamDTO(Long id, String name, String slug, String shortName, String nameCode) {
        public Long getId() { return id; }
        public String getName() { return name; }
        public String getSlug() { return slug; }
        public String getShortName() { return shortName; }
        public String getNameCode() { return nameCode; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatusDTO(Integer code, String description, String type) {
        public Integer getCode() { return code; }
        public String getDescription() { return description; }
        public String getType() { return type; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RoundInfo(Integer round) {
        public Integer getRound() { return round; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScoreDetailDTO(Integer current, Integer display, Integer period1, Integer period2) {
        public Integer getCurrent() { return current; }
        public Integer getDisplay() { return display; }
        public Integer getPeriod1() { return period1; }
        public Integer getPeriod2() { return period2; }
    }
}
