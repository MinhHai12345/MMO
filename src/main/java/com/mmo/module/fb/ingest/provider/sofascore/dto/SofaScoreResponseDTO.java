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
        public List<UniqueTournamentDTO> getUniqueTournaments() { return uniqueTournaments; }
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UniqueTournamentDTO(@JsonProperty("id") Long id, String name, String slug,
            String primaryColorHex, String secondaryColorHex, Long userCount, CategoryDTO category) {
        public Long getId(){return id;} public String getName(){return name;} public String getSlug(){return slug;}
        public String getPrimaryColorHex(){return primaryColorHex;} public String getSecondaryColorHex(){return secondaryColorHex;}
        public Long getUserCount(){return userCount;} public CategoryDTO getCategory(){return category;}
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CategoryDTO(Long id,String name,String slug,String flag,String alpha2,SportDTO sport) {
        public Long getId(){return id;} public String getName(){return name;} public String getSlug(){return slug;}
        public String getFlag(){return flag;} public String getAlpha2(){return alpha2;} public SportDTO getSport(){return sport;}
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SportDTO(Integer id,String name,String slug) {
        public Integer getId(){return id;} public String getName(){return name;} public String getSlug(){return slug;}
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeasonsResponseDTO(@JsonProperty("seasons") List<SeasonDTO> seasons) {
        public List<SeasonDTO> getSeasons(){return seasons;}
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeasonDTO(@JsonProperty("id") Long id,String name,String year,@JsonProperty("isCurrent") boolean current) {
        public Long getId(){return id;} public String getName(){return name;} public String getYear(){return year;}
        public boolean isCurrent(){return current;}
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StandingsResponseDTO(@JsonProperty("standings") List<StandingGroupDTO> standings) {
        public List<StandingGroupDTO> getStandings(){return standings;}
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StandingGroupDTO(Long id,String type,String name,List<StandingRowDTO> rows) {
        public Long getId(){return id;} public String getType(){return type;} public String getName(){return name;}
        public List<StandingRowDTO> getRows(){return rows;}
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StandingRowDTO(Integer position,Integer matches,Integer wins,Integer draws,Integer losses,Integer points,
            Integer scoresFor,Integer scoresAgainst,String scoreDiffFormatted,TeamDTO team) {
        public Integer getPosition(){return position;} public Integer getMatches(){return matches;} public Integer getWins(){return wins;}
        public Integer getDraws(){return draws;} public Integer getLosses(){return losses;} public Integer getPoints(){return points;}
        public Integer getScoresFor(){return scoresFor;} public Integer getScoresAgainst(){return scoresAgainst;}
        public String getScoreDiffFormatted(){return scoreDiffFormatted;} public TeamDTO getTeam(){return team;}
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TeamDTO(Long id,String name,String slug,String shortName,String nameCode,Long userCount,Integer ranking,CountryDTO country) {
        public Long getId(){return id;} public String getName(){return name;} public String getSlug(){return slug;}
        public String getShortName(){return shortName;} public String getNameCode(){return nameCode;} public Long getUserCount(){return userCount;}
        public Integer getRanking(){return ranking;} public CountryDTO getCountry(){return country;}
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CountryDTO(String alpha2,String alpha3,String name) {
        public String getAlpha2(){return alpha2;} public String getAlpha3(){return alpha3;} public String getName(){return name;}
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MatchesResponseDTO(@JsonProperty("events") List<EventDTO> events,@JsonProperty("hasNextPage") boolean hasNextPage) {
        public List<EventDTO> getEvents(){return events;} public boolean isHasNextPage(){return hasNextPage;}
        public boolean getHasNextPage(){return hasNextPage;}
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MatchResponseDTO(@JsonProperty("event") EventDTO event) { public EventDTO getEvent(){return event;} }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EventDTO(@JsonProperty("id") Long id,String slug,Long startTimestamp,Boolean hasXg,
            EventTournamentDTO tournament,EventSeasonDTO season,TeamDTO homeTeam,TeamDTO awayTeam,
            ScoreDetailDTO homeScore,ScoreDetailDTO awayScore,StatusDTO status,RoundInfo roundInfo) {
        public Long getId(){return id;} public String getSlug(){return slug;} public Long getStartTimestamp(){return startTimestamp;}
        public Boolean getHasXg(){return hasXg;} public EventTournamentDTO getTournament(){return tournament;}
        public EventSeasonDTO getSeason(){return season;} public TeamDTO getHomeTeam(){return homeTeam;}
        public TeamDTO getAwayTeam(){return awayTeam;} public ScoreDetailDTO getHomeScore(){return homeScore;}
        public ScoreDetailDTO getAwayScore(){return awayScore;} public StatusDTO getStatus(){return status;}
        public RoundInfo getRoundInfo(){return roundInfo;}
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EventTournamentDTO(String name,String slug) { public String getName(){return name;} public String getSlug(){return slug;} }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EventSeasonDTO(String name,String year) { public String getName(){return name;} public String getYear(){return year;} }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatusDTO(Integer code,String description,String type) {
        public Integer getCode(){return code;} public String getDescription(){return description;} public String getType(){return type;}
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RoundInfo(Integer round) { public Integer getRound(){return round;} }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScoreDetailDTO(Integer current,Integer display,Integer period1,Integer period2) {
        public Integer getCurrent(){return current;} public Integer getDisplay(){return display;}
        public Integer getPeriod1(){return period1;} public Integer getPeriod2(){return period2;}
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatisticsResponseDTO(@JsonProperty("statistics") List<PeriodStatisticsDTO> statistics) {
        public List<PeriodStatisticsDTO> getStatistics(){return statistics;}
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PeriodStatisticsDTO(String period,List<GroupDTO> groups) {
        public String getPeriod(){return period;} public List<GroupDTO> getGroups(){return groups;}
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GroupDTO(String groupName,List<StatisticItemDTO> statisticsItems) {
        public String getGroupName(){return groupName;} public List<StatisticItemDTO> getStatisticsItems(){return statisticsItems;}
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatisticItemDTO(String name,String key,String home,String away,BigDecimal homeValue,BigDecimal awayValue,
            Integer compareCode,String statisticsType,String valueType) {
        public String getName(){return name;} public String getKey(){return key;} public String getHome(){return home;}
        public String getAway(){return away;} public BigDecimal getHomeValue(){return homeValue;} public BigDecimal getAwayValue(){return awayValue;}
        public Integer getCompareCode(){return compareCode;} public String getStatisticsType(){return statisticsType;}
        public String getValueType(){return valueType;}
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OddsResponseDTO(Map<Long,MatchOddDetailDTO> odds) { public Map<Long,MatchOddDetailDTO> getOdds(){return odds;} }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MatchOddDetailDTO(String id,Integer marketId,String marketName,String marketGroup,List<ChoiceDTO> choices) {
        public String getId(){return id;} public Integer getMarketId(){return marketId;} public String getMarketName(){return marketName;}
        public String getMarketGroup(){return marketGroup;} public List<ChoiceDTO> getChoices(){return choices;}
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChoiceDTO(String name,String fractionalValue,Long sourceId) {
        public String getName(){return name;} public String getFractionalValue(){return fractionalValue;} public Long getSourceId(){return sourceId;}
    }
}
