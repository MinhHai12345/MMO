package com.mmo.module.fb.ingest.application.port.out.provider;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.model.FixtureQuery;
import com.mmo.module.fb.ingest.provider.model.ProviderFixture;
import java.util.List;

/** Application-owned outbound capability for fixture retrieval. */
public interface FixtureIngestionPort {
    Provider getProvider();
    List<ProviderFixture> fetchFixtures(FixtureQuery query);
}
