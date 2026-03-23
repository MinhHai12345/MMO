package com.mmo.understat.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class LeagueData {
    public List<MatchDate> dates;
    public List<PlayerData> players;
    public Map<String, TeamData> teams;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MatchDate {
        public String id;
        public boolean isResult;
        public TeamInfo h;
        public TeamInfo a;
        public Map<String, String> goals;
        public Map<String, String> xG;
        public String datetime;

        @Getter
        @Setter
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class TeamInfo {

            @JsonProperty("id")
            private String id;

            @JsonProperty("title")
            private String title;

            @JsonProperty("short_title")
            private String shortTitle;
        }
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlayerData {
        public String player_name;
        public String team_title;
        public double xG;
        public double xA;
        public int goals;
        public int shots;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TeamData {
        public String id;
        public String title;
        public List<HistoryData> history;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class HistoryData {
            private String h_a;
            private double xG;
            private double xGA;

            private int scored;
            private int missed;

            private double xpts;
            private int pts;

            private String result;
            private String date;

            private Ppda ppda;
            @JsonProperty("ppda_allowed")
            private Ppda ppdaAllowed;

            private int deep;
            private int deep_allowed;

            @Data
            public static class Ppda {
                private int att;
                private int def;

                public double getIntensity() {
                    return def == 0 ? 0 : (double) att / def;
                }
            }
        }
    }
}
