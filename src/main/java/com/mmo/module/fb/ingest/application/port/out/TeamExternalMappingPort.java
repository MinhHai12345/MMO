package com.mmo.module.fb.ingest.application.port.out;

import com.mmo.module.fb.ingest.enums.Provider;
import java.util.List;

/** Application-owned read port for provider-to-canonical team mappings. */
public interface TeamExternalMappingPort {
    List<TeamMapping> findAllByProvider(Provider provider);

    record TeamMapping(Long teamId, String externalId) {}
}
