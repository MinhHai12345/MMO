package com.mmo.module.fb.ingest.provider.model;

import java.time.LocalDate;

public record ProviderSeason(String year, boolean current, LocalDate startDate, LocalDate endDate) {
}
