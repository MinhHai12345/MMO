package com.mmo.module.fb.ingest.application.port.in;

import com.mmo.module.fb.ingest.application.model.provider.ProviderLeague;

import java.util.List;

/** Read-only use case for retrieving one SofaScore tournament and its seasons. */
public interface SofaScoreLeagueLookupUseCase {
    List<ProviderLeague> fetchLeague(Long tournamentId);
}
