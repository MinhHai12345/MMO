package com.mmo.module.fb.ingest.application.port.out;

import com.mmo.module.fb.ingest.enums.Provider;
import java.util.List;

/** Application-owned read port for provider-to-canonical league mappings. */
public interface LeagueExternalMappingPort {
    List<LeagueMapping> findAllByProvider(Provider provider);

    record LeagueMapping(Long leagueId, String externalId, String identify,
                         String name, String country, String logoUrl) {}
}
