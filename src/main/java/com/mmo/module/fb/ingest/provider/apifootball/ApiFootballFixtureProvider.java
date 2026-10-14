package com.mmo.module.fb.ingest.provider.apifootball;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mmo.module.fb.ingest.provider.apifootball.client.ApiFootballClient;
import com.mmo.module.fb.ingest.provider.apifootball.dto.ApiFootballDtos.FixtureItem;
import com.mmo.module.fb.ingest.provider.apifootball.dto.ApiFootballResponse;
import com.mmo.module.fb.ingest.provider.apifootball.mapper.ApiFootballFixtureMapper;
import com.mmo.module.fb.ingest.provider.model.FixtureQuery;
import com.mmo.module.fb.ingest.provider.model.ProviderFixture;
import com.mmo.module.fb.ingest.provider.spi.FixtureProvider;
import com.mmo.module.fb.domain.enums.Provider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiFootballFixtureProvider implements FixtureProvider {

    private static final TypeReference<ApiFootballResponse<FixtureItem>> RESPONSE_TYPE =
            new TypeReference<>() {};
    private static final int MAX_PAGES = 100;

    private final ApiFootballClient client;
    private final ApiFootballFixtureMapper mapper;

    @Override
    public Provider getProvider() {
        return Provider.API_FOOTBALL;
    }

    @Override
    public List<ProviderFixture> fetchFixtures(FixtureQuery query) {
        Map<String, Object> baseQuery = new LinkedHashMap<>();
        baseQuery.put("league", query.leagueId());
        baseQuery.put("season", query.season());
        if (query.from() != null) baseQuery.put("from", query.from().toString());
        if (query.to() != null) baseQuery.put("to", query.to().toString());

        List<ProviderFixture> fixtures = new ArrayList<>();
        int page = 1;
        int totalPages = 1;

        do {
            Map<String, Object> params = new LinkedHashMap<>(baseQuery);
            params.put("page", page);
            ApiFootballResponse<FixtureItem> response = client.get("/fixtures", params, RESPONSE_TYPE);
            List<FixtureItem> pageItems = response.response() == null ? List.of() : response.response();
            pageItems.stream().map(mapper::toModel).filter(java.util.Objects::nonNull)
                    .forEach(fixtures::add);

            totalPages = response.paging() != null && response.paging().total() != null
                    ? response.paging().total() : page;
            if (page >= totalPages || pageItems.isEmpty()) break;
            page++;
        } while (page <= MAX_PAGES);

        if (page > MAX_PAGES) {
            log.warn("API-Football fixture pagination reached {}-page safety limit. Query={}",
                    MAX_PAGES, baseQuery);
        }
        return fixtures;
    }
}
