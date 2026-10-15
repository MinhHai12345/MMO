package com.mmo.module.fb.ingest.service;

import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.ingest.provider.model.ProviderLeague;
import java.util.List;

public interface LeaguePersistenceService {
    void persistLeagues(Provider provider, List<ProviderLeague> leagues);
}
