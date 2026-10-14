package com.mmo.module.fb.ingest.provider.spi;

import com.mmo.module.fb.ingest.provider.model.OddsQuery;
import com.mmo.module.fb.ingest.provider.model.ProviderOdds;

import java.util.List;

public interface OddsProvider {
    List<ProviderOdds> fetchOdds(OddsQuery query);
}
