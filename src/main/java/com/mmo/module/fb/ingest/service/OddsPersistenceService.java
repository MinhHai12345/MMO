package com.mmo.module.fb.ingest.service;

public interface OddsPersistenceService {
    void persistOdds(Long matchId, java.util.List<com.mmo.module.fb.ingest.provider.model.ProviderOdds> values);
}
