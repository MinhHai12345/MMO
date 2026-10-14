package com.mmo.module.fb.ingest.provider.apifootball;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.ingest.provider.apifootball.client.ApiFootballClient;
import com.mmo.module.fb.ingest.provider.apifootball.dto.ApiFootballDtos.OddsItem;
import com.mmo.module.fb.ingest.provider.apifootball.dto.ApiFootballResponse;
import com.mmo.module.fb.ingest.provider.apifootball.mapper.ApiFootballOddsMapper;
import com.mmo.module.fb.ingest.provider.model.OddsQuery;
import com.mmo.module.fb.ingest.provider.model.ProviderOdds;
import com.mmo.module.fb.ingest.provider.spi.OddsProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ApiFootballOddsProvider implements OddsProvider {

    private static final TypeReference<ApiFootballResponse<OddsItem>> RESPONSE_TYPE =
            new TypeReference<>() {
            };

    private final ApiFootballClient client;
    private final ApiFootballOddsMapper mapper;

    @Override
    public Provider getProvider() {
        return Provider.API_FOOTBALL;
    }

    @Override
    public List<ProviderOdds> fetchOdds(OddsQuery query) {
        ApiFootballResponse<OddsItem> response = client.get(
                "/odds", Map.of("fixture", query.fixtureExternalId()), RESPONSE_TYPE);
        return mapper.toModels(response.response());
    }
}
