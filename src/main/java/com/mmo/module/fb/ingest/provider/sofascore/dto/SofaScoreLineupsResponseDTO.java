package com.mmo.module.fb.ingest.provider.sofascore.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SofaScoreLineupsResponseDTO(
        @JsonProperty("home") LineupTeamDTO home,
        @JsonProperty("away") LineupTeamDTO away) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LineupTeamDTO(List<LineupPlayerDTO> players, String formation) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LineupPlayerDTO(PlayerDTO player, Integer shirtNumber, String position,
                                  Boolean substitute, Boolean captain,
                                  Map<String, Object> statistics) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlayerDTO(Long id, String name, String shortName, String slug,
                            String position, String nationality, Integer height,
                            Integer weight, Integer shirtNumber) {
    }

}
