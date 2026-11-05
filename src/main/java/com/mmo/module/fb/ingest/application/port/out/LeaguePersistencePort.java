package com.mmo.module.fb.ingest.application.port.out;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.model.ProviderLeague;
import java.util.List;

public interface LeaguePersistencePort {
    void persistLeagues(Provider provider, List<ProviderLeague> leagues);
}
