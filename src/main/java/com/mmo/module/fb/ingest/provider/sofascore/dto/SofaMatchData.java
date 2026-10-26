package com.mmo.module.fb.ingest.provider.sofascore.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SofaMatchData(
        @JsonProperty("event") SofaMatchesData.SofaEventDTO event
) {
    public SofaMatchesData.SofaEventDTO getEvent() {
        return event;
    }
}
