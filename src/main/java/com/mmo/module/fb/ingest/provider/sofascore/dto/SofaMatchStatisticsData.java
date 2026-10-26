package com.mmo.module.fb.ingest.provider.sofascore.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SofaMatchStatisticsData(
        @JsonProperty("statistics") List<PeriodStatisticsDTO> statistics
) {
    public List<PeriodStatisticsDTO> getStatistics() {
        return statistics;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PeriodStatisticsDTO(String period, List<GroupDTO> groups) {
        public String getPeriod() { return period; }
        public List<GroupDTO> getGroups() { return groups; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GroupDTO(String groupName, List<StatisticItemDTO> statisticsItems) {
        public String getGroupName() { return groupName; }
        public List<StatisticItemDTO> getStatisticsItems() { return statisticsItems; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatisticItemDTO(String name, String key, String home, String away,
                                   BigDecimal homeValue, BigDecimal awayValue, Integer compareCode,
                                   String statisticsType, String valueType) {
        public String getName() { return name; }
        public String getKey() { return key; }
        public String getHome() { return home; }
        public String getAway() { return away; }
        public BigDecimal getHomeValue() { return homeValue; }
        public BigDecimal getAwayValue() { return awayValue; }
        public Integer getCompareCode() { return compareCode; }
        public String getStatisticsType() { return statisticsType; }
        public String getValueType() { return valueType; }
    }
}
