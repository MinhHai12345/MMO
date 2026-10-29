package com.mmo.module.fb.ingest.provider.understats.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Transport records for Understat's direct AJAX JSON API. */
public final class UnderstatDtos {

    private UnderstatDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LeagueData(
            List<Match> dates,
            Map<String, LeagueTeam> teams
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LeagueTeam(
            String id,
            String title,
            List<TeamHistoryMatch> history
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TeamRef(
            String id,
            String title,
            @JsonProperty("short_title") String shortTitle
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Goals(Integer h, Integer a) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Xg(BigDecimal h, BigDecimal a) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Forecast(BigDecimal w, BigDecimal d, BigDecimal l) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Match(
            String id,
            Boolean isResult,
            TeamRef h,
            TeamRef a,
            Goals goals,
            @JsonProperty("xG") Xg xg,
            String datetime,
            Forecast forecast
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Ppda(Integer att, Integer def) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TeamHistoryMatch(
            @JsonProperty("h_a") String homeAway,
            BigDecimal xG,
            BigDecimal xGA,
            BigDecimal npxG,
            BigDecimal npxGA,
            Ppda ppda,
            @JsonProperty("ppda_allowed") Ppda ppdaAllowed,
            Integer deep,
            @JsonProperty("deep_allowed") Integer deepAllowed,
            Integer scored,
            Integer missed,
            BigDecimal xpts,
            String result,
            String date,
            Integer wins,
            Integer draws,
            Integer loses,
            Integer pts,
            @JsonProperty("npxGD") BigDecimal npxGD,
            String id
    ) {
    }
}
