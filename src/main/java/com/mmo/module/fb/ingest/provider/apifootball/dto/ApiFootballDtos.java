package com.mmo.module.fb.ingest.provider.apifootball.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public final class ApiFootballDtos {
    private ApiFootballDtos() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LeagueItem(LeagueDto league, CountryDto country, List<LeagueSeasonDto> seasons) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LeagueDto(Long id, String name, String type, String logo) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CountryDto(String name, String code, String flag) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LeagueSeasonDto(Integer year, Boolean current, String start, String end) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TeamItem(TeamDto team, VenueDto venue) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TeamDto(Long id, String name, String code, String country, Integer founded,
                          Boolean national, String logo) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VenueDto(Long id, String name, String address, String city, Integer capacity, String surface) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FixtureItem(FixtureDto fixture, FixtureLeagueDto league, FixtureTeamsDto teams,
                              FixtureGoalsDto goals, FixtureScoreDto score) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FixtureDto(Long id, String referee, String timezone, String date, Long timestamp,
                             FixtureStatusDto status, VenueDto venue) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FixtureStatusDto(@JsonProperty("long") String longName, @JsonProperty("short") String shortName, Integer elapsed, Integer extra) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FixtureLeagueDto(Long id, String name, String country, String season, String round) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FixtureTeamsDto(FixtureTeamDto home, FixtureTeamDto away) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FixtureTeamDto(Long id, String name, String logo, Boolean winner) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FixtureGoalsDto(Integer home, Integer away) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FixtureScoreDto(FixtureGoalsDto halftime, FixtureGoalsDto fulltime,
                                  FixtureGoalsDto extratime, FixtureGoalsDto penalty) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StandingsItem(StandingsLeagueDto league) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StandingsLeagueDto(Long id, String name, String country, String season,
                                     List<List<StandingRowDto>> standings) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StandingRowDto(Integer rank, FixtureTeamDto team, Integer points, Integer goalsDiff,
                                 String group, String form, String status, StandingStatsDto all,
                                 StandingStatsDto home, StandingStatsDto away, String description) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StandingStatsDto(Integer played, Integer win, Integer draw, Integer lose, GoalsDto goals) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GoalsDto(@JsonProperty("for") Integer forGoals, @JsonProperty("against") Integer againstGoals) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OddsItem(FixtureRefDto fixture, LeagueRefDto league, String update, List<OddsBookmakerDto> bookmakers) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FixtureRefDto(Long id) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LeagueRefDto(Long id, String name, String country, Integer season, String logo, String flag, String round) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OddsBookmakerDto(Long id, String name, List<OddsBetDto> bets) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OddsBetDto(Long id, String name, List<OddsValueDto> values) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OddsValueDto(String value, String odd, String handicap, String main, String suspended) {}
}
