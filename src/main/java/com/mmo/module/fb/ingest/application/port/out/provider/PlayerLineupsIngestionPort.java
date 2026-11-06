package com.mmo.module.fb.ingest.application.port.out.provider;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.application.model.provider.ProviderLineups;
import java.util.Optional;

/** Application-owned outbound capability for lineups and player match metrics. */
public interface PlayerLineupsIngestionPort {
    Provider getProvider();
    Optional<ProviderLineups> fetchLineups(String externalMatchId);
}
