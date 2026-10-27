package com.mmo.module.fb.ingest.provider.footballdata.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class FootballDataDtos {
    private FootballDataDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AreaDto(Long id, String name, String code, String flag) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeasonDto(Long id, LocalDate startDate, LocalDate endDate,
                            Integer currentMatchday) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CompetitionDto(Long id, String name, String code, String type,
                                 AreaDto area, String emblem, SeasonDto currentSeason,
                                 List<SeasonDto> seasons) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CompetitionListDto(Integer count, List<CompetitionDto> competitions) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TeamDto(Long id, String name, String shortName, String tla, String crest,
                          Integer founded, String venue) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TeamListDto(Integer count, List<TeamDto> teams) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScorePairDto(Integer home, Integer away) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScoreDto(String winner, String duration, ScorePairDto fullTime,
                           ScorePairDto halfTime, ScorePairDto regularTime, ScorePairDto extraTime,
                           ScorePairDto penalties) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MatchDto(Long id, OffsetDateTime utcDate, String status, Integer matchday,
                           String stage, String group, String venue, CompetitionDto competition,
                           SeasonDto season, TeamDto homeTeam, TeamDto awayTeam, ScoreDto score) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MatchListDto(Integer count, List<MatchDto> matches) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StandingRowDto(Integer position, TeamDto team, Integer playedGames,
                                 Integer won, Integer draw, Integer lost, Integer points,
                                 Integer goalsFor, Integer goalsAgainst, Integer goalDifference,
                                 String form, String group) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StandingTableDto(String stage, String type, String group, List<StandingRowDto> table) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StandingsDto(CompetitionDto competition, SeasonDto season,
                               List<StandingTableDto> standings) {
    }
}
