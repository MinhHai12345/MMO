package com.mmo.module.fb.ingest.provider.model.sofa;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SofaMatchData {
    @JsonProperty("event")
    private SofaMatchesData.SofaEventDTO event;

}
