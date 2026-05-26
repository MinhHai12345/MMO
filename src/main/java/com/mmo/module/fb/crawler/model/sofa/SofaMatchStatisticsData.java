package com.mmo.module.fb.crawler.model.sofa;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SofaMatchStatisticsData {

    @JsonProperty("statistics")
    private List<PeriodStatisticsDTO> statistics;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PeriodStatisticsDTO {
        private String period; // "ALL", "1ST", "2ND"
        private List<GroupDTO> groups;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GroupDTO {
        private String groupName;
        private List<StatisticItemDTO> statisticsItems;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StatisticItemDTO {
        private String name;
        private String key;
        private String home;
        private String away;
        private Double homeValue;
        private Double awayValue;
        private Integer compareCode;
        private String statisticsType;
        private String valueType;
    }
}