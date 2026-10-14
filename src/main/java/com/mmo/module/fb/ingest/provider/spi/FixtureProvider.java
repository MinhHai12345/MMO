package com.mmo.module.fb.ingest.provider.spi;

import com.mmo.module.fb.ingest.provider.model.FixtureQuery;
import com.mmo.module.fb.ingest.provider.model.ProviderFixture;

import java.util.List;

/**
 * Source-neutral contract for retrieving fixtures.
 * Implementations translate source-specific payloads into ProviderFixture.
 */
public interface FixtureProvider {
    List<ProviderFixture> fetchFixtures(FixtureQuery query);
}
