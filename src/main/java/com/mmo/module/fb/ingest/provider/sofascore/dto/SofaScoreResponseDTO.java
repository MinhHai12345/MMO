package com.mmo.module.fb.ingest.provider.sofascore.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Compact collection of SofaScore transport response records. */
public final class SofaScoreResponseDTO {
    private SofaScoreResponseDTO() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TournamentsResponseDTO(@JsonProperty("uniqueTournaments") List<UniqueTournamentDTO> uniqueTournaments) {
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UniqueTournamentDTO(@JsonProperty("id") Long id, String name, String slug,
            String primaryColorHex, String secondaryColorHex, Long userCount, CategoryDTO category) {
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CategoryDTO(Long id,String name,String slug,String flag,String alpha2,SportDTO sport) {
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SportDTO(Integer id,String name,String slug) {
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeasonsResponseDTO(@JsonProperty("seasons") List<SeasonDTO> seasons) {
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeasonDTO(@JsonProperty("id") Long id,String name,String year,@JsonProperty("isCurrent") boolean current) {
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StandingsResponseDTO(@JsonProperty("standings") List<StandingGroupDTO> standings) {
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StandingGroupDTO(Long id,String type,String name,List<StandingRowDTO> rows) {
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StandingRowDTO(Integer position,Integer matches,Integer wins,Integer draws,Integer losses,Integer points,
            Integer scoresFor,Integer scoresAgainst,String scoreDiffFormatted,TeamDTO team) {
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TeamDTO(Long id,String name,String slug,String shortName,String nameCode,Long userCount,Integer ranking,CountryDTO country) {
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CountryDTO(String alpha2,String alpha3,String name) {
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MatchesResponseDTO(@JsonProperty("events") List<EventDTO> events,@JsonProperty("hasNextPage") boolean hasNextPage) {
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MatchResponseDTO(@JsonProperty("event") EventDTO event) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EventDTO(@JsonProperty("id") Long id,String slug,Long startTimestamp,Boolean hasXg,
            EventTournamentDTO tournament,EventSeasonDTO season,TeamDTO homeTeam,TeamDTO awayTeam,
            ScoreDetailDTO homeScore,ScoreDetailDTO awayScore,StatusDTO status,RoundInfo roundInfo) {
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EventTournamentDTO(String name,String slug) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EventSeasonDTO(String name,String year) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatusDTO(Integer code,String description,String type) {
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RoundInfo(Integer round) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScoreDetailDTO(Integer current,Integer display,Integer period1,Integer period2) {
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatisticsResponseDTO(@JsonProperty("statistics") List<PeriodStatisticsDTO> statistics) {
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PeriodStatisticsDTO(String period,List<GroupDTO> groups) {
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GroupDTO(String groupName,List<StatisticItemDTO> statisticsItems) {
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatisticItemDTO(String name,String key,String home,String away,BigDecimal homeValue,BigDecimal awayValue,
            Integer compareCode,String statisticsType,String valueType) {
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OddsResponseDTO(Map<Long,MatchOddDetailDTO> odds) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MatchOddDetailDTO(String id,Integer marketId,String marketName,String marketGroup,List<ChoiceDTO> choices) {
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChoiceDTO(String name,String fractionalValue,Long sourceId) {
    }
}
