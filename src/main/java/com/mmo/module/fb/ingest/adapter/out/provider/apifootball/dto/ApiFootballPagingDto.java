package com.mmo.module.fb.ingest.adapter.out.provider.apifootball.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiFootballPagingDto(Integer current, Integer total) {
}
