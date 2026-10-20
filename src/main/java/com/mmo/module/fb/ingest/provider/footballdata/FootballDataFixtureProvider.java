package com.mmo.module.fb.ingest.provider.footballdata;

import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.ingest.provider.footballdata.client.FootballDataClient;
import com.mmo.module.fb.ingest.provider.footballdata.dto.FootballDataDtos.MatchListDto;
import com.mmo.module.fb.ingest.provider.footballdata.mapper.FootballDataMapper;
import com.mmo.module.fb.ingest.provider.model.FixtureQuery;
import com.mmo.module.fb.ingest.provider.model.ProviderFixture;
import com.mmo.module.fb.ingest.provider.spi.FixtureProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class FootballDataFixtureProvider implements FixtureProvider {
    private final FootballDataClient client;
    private final FootballDataMapper mapper;

    @Override
    public Provider getProvider() { return Provider.FOOTBALL_DATA; }

    @Override
    public List<ProviderFixture> fetchFixtures(FixtureQuery query) {
        if (query == null || query.leagueId() == null) return List.of();
        UriComponentsBuilder uri = UriComponentsBuilder.fromPath("/competitions/{id}/matches");
        if (StringUtils.hasText(query.season())) uri.queryParam("season", query.season());
        LocalDate from = query.from(), to = query.to();
        if (from != null) uri.queryParam("dateFrom", from);
        if (to != null) uri.queryParam("dateTo", to);
        String path = uri.buildAndExpand(query.leagueId()).encode().toUriString();
        MatchListDto response = client.get(path, MatchListDto.class);
        if (response == null || response.matches() == null) return List.of();
        return response.matches().stream().map(mapper::toFixture).filter(Objects::nonNull).toList();
    }
}