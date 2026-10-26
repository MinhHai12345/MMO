package com.mmo.module.fb.ingest.provider.sofascore.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SofaStandingsData(@JsonProperty("standings") List<StandingGroupDTO> standings) {
    public List<StandingGroupDTO> getStandings() { return standings; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StandingGroupDTO(Long id, String type, String name, List<StandingRowDTO> rows) {
        public Long getId() { return id; }
        public String getType() { return type; }
        public String getName() { return name; }
        public List<StandingRowDTO> getRows() { return rows; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StandingRowDTO(Integer position, Integer matches, Integer wins, Integer draws,
                                 Integer losses, Integer points, Integer scoresFor, Integer scoresAgainst,
                                 String scoreDiffFormatted, TeamDTO team) {
        public Integer getPosition() { return position; }
        public Integer getMatches() { return matches; }
        public Integer getWins() { return wins; }
        public Integer getDraws() { return draws; }
        public Integer getLosses() { return losses; }
        public Integer getPoints() { return points; }
        public Integer getScoresFor() { return scoresFor; }
        public Integer getScoresAgainst() { return scoresAgainst; }
        public String getScoreDiffFormatted() { return scoreDiffFormatted; }
        public TeamDTO getTeam() { return team; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TeamDTO(Long id, String name, String slug, String shortName, String nameCode,
                          Long userCount, Integer ranking, CountryDTO country) {
        public Long getId() { return id; }
        public String getName() { return name; }
        public String getSlug() { return slug; }
        public String getShortName() { return shortName; }
        public String getNameCode() { return nameCode; }
        public Long getUserCount() { return userCount; }
        public Integer getRanking() { return ranking; }
        public CountryDTO getCountry() { return country; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CountryDTO(String alpha2, String alpha3, String name) {
        public String getAlpha2() { return alpha2; }
        public String getAlpha3() { return alpha3; }
        public String getName() { return name; }
    }
}
