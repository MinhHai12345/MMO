package com.mmo.module.fb.ingest.provider.understats;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.model.ProviderSeason;
import com.mmo.module.fb.ingest.provider.model.SeasonQuery;
import com.mmo.module.fb.ingest.provider.spi.SeasonProvider;
import com.mmo.module.fb.ingest.provider.understats.dto.UnderStatDtos;
import com.mmo.module.fb.ingest.provider.understats.mapper.UnderStatLeagueMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/** Discovers a single league's seasons from UnderStat's independent stat-data resource. */
@Component
@RequiredArgsConstructor
public class UnderStatSeasonProvider implements SeasonProvider {
    private static final TypeReference<UnderStatDtos.StatDataResponse> RESPONSE_TYPE =
            new TypeReference<>() { };

    private final UnderStatClient client;
    private final UnderStatLeagueMapper mapper;

    @Override
    public Provider getProvider() {
        return Provider.UNDER_STATS;
    }

    @Override
    public List<ProviderSeason> fetchSeasons(SeasonQuery query) {
        if (query == null || !StringUtils.hasText(query.leagueExternalId())) {
            return List.of();
        }
        UnderStatDtos.StatDataResponse response = client.fetchStatData(RESPONSE_TYPE);
        return mapper.toProviderSeasons(response, query.leagueExternalId());
    }
}
