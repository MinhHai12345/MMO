package com.mmo.module.fb.ingest.provider.apifootball.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiFootballPagingDto(Integer current, Integer total) {
}
