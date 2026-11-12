package com.mmo.module.fb.ingest.application.model;

import java.time.LocalDate;

/**
 * Read-only SofaScore diagnostic parameters.
 *
 * @param tournamentId SofaScore unique tournament ID
 * @param season season label, for example 2026/2027
 * @param from first fixture date (inclusive)
 * @param to last fixture date (inclusive), at most 31 days after from
 * @param matchId optional SofaScore event ID for lineup validation
 */
public record SofaScoreDiagnosticRequest(
        Long tournamentId,
        String season,
        LocalDate from,
        LocalDate to,
        Long matchId
) {
}
