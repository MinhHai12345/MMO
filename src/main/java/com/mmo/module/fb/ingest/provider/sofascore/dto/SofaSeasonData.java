package com.mmo.module.fb.ingest.provider.sofascore.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SofaSeasonData(@JsonProperty("seasons") List<SeasonDTO> seasons) {
    public List<SeasonDTO> getSeasons() { return seasons; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeasonDTO(@JsonProperty("id") Long id, String name, String year,
                            @JsonProperty("isCurrent") boolean current) {
        public Long getId() { return id; }
        public String getName() { return name; }
        public String getYear() { return year; }
        public boolean isCurrent() { return current; }
    }
}
