package com.mmo.module.fb.ingest.provider.model;

import java.time.Instant;

/** Canonical fixture representation shared by ingestion services and all providers. */
public record ProviderFixture(
        String externalId,
        String homeTeamExternalId,
        String awayTeamExternalId,
        Instant kickoff,
        String round,
        String venue,
        String referee,
        String status,
        Integer homeScore,
        Integer awayScore
) {
}
