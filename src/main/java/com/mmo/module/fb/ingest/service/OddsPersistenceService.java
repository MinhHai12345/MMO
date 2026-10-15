package com.mmo.module.fb.ingest.service;

import com.mmo.module.fb.ingest.provider.model.ProviderOdds;

import java.util.List;

public interface OddsPersistenceService {
    void persistOdds(Long matchId, List<ProviderOdds> values);
}
