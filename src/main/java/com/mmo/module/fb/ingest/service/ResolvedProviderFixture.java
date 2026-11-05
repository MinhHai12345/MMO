package com.mmo.module.fb.ingest.service;

import com.mmo.module.fb.ingest.provider.model.ProviderFixture;

/** A provider fixture with its already-resolved canonical home/away team IDs. */
public record ResolvedProviderFixture(Long homeTeamId, Long awayTeamId, ProviderFixture fixture) {
}
