package com.mmo.module.fb.ingest.application.model.masterdata;

import com.mmo.module.fb.ingest.application.model.provider.ProviderLeague;
import com.mmo.module.fb.ingest.application.model.provider.ProviderSeason;
import com.mmo.module.fb.ingest.application.model.provider.ProviderTeam;
import com.mmo.module.fb.ingest.enums.Provider;

import java.util.List;

/** Read-only catalogue snapshot returned by provider discovery. */
public record ProviderMasterDataDiscoveryReport(
        String status,
        List<ProviderResult> providers,
        List<String> conflicts) {

    public record ProviderResult(
            Provider provider,
            String status,
            List<ProviderLeague> leagues,
            List<ProviderSeason> seasons,
            List<ProviderTeam> teams,
            List<String> warnings,
            String error) { }

    public record TeamQuery(
            Provider provider,
            Long leagueId,
            String season,
            String leagueIdentify) { }
}
