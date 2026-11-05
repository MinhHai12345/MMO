package com.mmo.module.fb.ingest.provider.spi;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.model.ProviderLineups;

import java.util.Optional;

/** Retrieves provider-neutral lineups and player match metrics for one fixture. */
public interface PlayerLineupsProvider {
    Provider getProvider();
    Optional<ProviderLineups> fetchLineups(String externalMatchId);
}
