package com.mmo.module.fb.ingest.application.model.masterdata;

import java.util.List;

public record MasterDataInitializationReport(
        String mode,
        int leaguesToCreate,
        int leaguesExisting,
        int seasonsToCreate,
        int seasonsExisting,
        int teamsToCreate,
        int teamsExisting,
        int mappingsToRegister,
        List<String> validationErrors) {

    public boolean valid() {
        return validationErrors == null || validationErrors.isEmpty();
    }
}
