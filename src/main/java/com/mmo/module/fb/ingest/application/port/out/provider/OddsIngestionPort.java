package com.mmo.module.fb.ingest.application.port.out.provider;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.application.model.provider.OddsQuery;
import com.mmo.module.fb.ingest.provider.model.ProviderOdds;
import java.util.List;

/** Application-owned outbound capability for odds retrieval. */
public interface OddsIngestionPort {
    Provider getProvider();
    List<ProviderOdds> fetchOdds(OddsQuery query);
}
