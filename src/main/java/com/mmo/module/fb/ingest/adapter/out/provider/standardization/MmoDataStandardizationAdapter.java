package com.mmo.module.fb.ingest.adapter.out.provider.standardization;

import com.mmo.module.fb.ingest.application.port.out.MmoDataStandardizationPort;
import com.mmo.module.fb.ingest.provider.model.MmoProviderDataStandardizer;
import org.springframework.stereotype.Component;

/** Adapter exposing shared MMO name standardization to application use cases. */
@Component
public class MmoDataStandardizationAdapter implements MmoDataStandardizationPort {
    @Override
    public String leagueName(String providerName) {
        return MmoProviderDataStandardizer.leagueName(providerName);
    }

    @Override
    public String teamName(String providerName) {
        return MmoProviderDataStandardizer.standardTeamName(providerName);
    }
}
