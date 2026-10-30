package com.mmo.module.fb.ingest.provider.understats;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.model.ProviderLeague;
import com.mmo.module.fb.ingest.provider.spi.LeagueProvider;
import com.mmo.module.fb.ingest.provider.understats.dto.UnderStatDtos;
import com.mmo.module.fb.ingest.provider.understats.mapper.UnderStatLeagueMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** Discovers UnderStat leagues and available seasons from its monthly stat catalogue. */
@Component
@RequiredArgsConstructor
public class UnderStatLeagueProvider implements LeagueProvider {
    private static final TypeReference<UnderStatDtos.StatDataResponse> RESPONSE_TYPE =
            new TypeReference<>() { };

    private final UnderStatClient client;
    private final UnderStatLeagueMapper mapper;

    @Override
    public Provider getProvider() {
        return Provider.UNDER_STATS;
    }

    @Override
    public List<ProviderLeague> fetchLeagues(List<Long> externalLeagueIds) {
        UnderStatDtos.StatDataResponse response = client.fetchStatData(RESPONSE_TYPE);
        return mapper.toProviderLeagues(response);
    }
}
