package com.mmo.module.fb.ingest.application.port.out;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.application.model.provider.ProviderLeague;
import java.util.List;

public interface LeaguePersistencePort {
    void persistLeagues(Provider provider, List<ProviderLeague> leagues);
}
