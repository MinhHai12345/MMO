package com.mmo.module.fb.ingest.provider.spi;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.model.FixtureQuery;
import com.mmo.module.fb.ingest.provider.model.ProviderFixture;

import java.util.List;

/** Source-neutral contract for retrieving fixtures. */
public interface FixtureProvider {
    Provider getProvider();
    List<ProviderFixture> fetchFixtures(FixtureQuery query);
}
