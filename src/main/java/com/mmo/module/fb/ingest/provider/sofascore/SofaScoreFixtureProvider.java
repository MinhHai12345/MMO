package com.mmo.module.fb.ingest.provider.sofascore;

import com.microsoft.playwright.Page;
import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.model.FixtureQuery;
import com.mmo.module.fb.ingest.provider.model.ProviderFixture;
import com.mmo.module.fb.ingest.provider.sofascore.client.AbstractSofaScoreProvider;
import com.mmo.module.fb.ingest.provider.sofascore.client.SofaScoreClient;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaScoreResponseDTO.MatchesResponseDTO;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaScoreResponseDTO.SeasonDTO;
import com.mmo.module.fb.ingest.provider.sofascore.mapper.SofaScoreFixtureMapper;
import com.mmo.module.fb.ingest.provider.spi.FixtureProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class SofaScoreFixtureProvider extends AbstractSofaScoreProvider implements FixtureProvider {
    private final SofaScoreFixtureMapper mapper;
    private final SofaScoreClient sofaScoreClient;
    private final AppProperties appProperties;

    @Override
    public Provider getProvider() {
        return Provider.SOFA_SCORE;
    }

    @Override
    public List<ProviderFixture> fetchFixtures(FixtureQuery query) {
        if (query == null || query.leagueId() == null) return List.of();
        return withPage(page -> {
            List<SeasonDTO> seasons =
                    sofaScoreClient.fetchSeasonByTournamentId(query.leagueId(), page);
            if (seasons == null || seasons.isEmpty())
                throw new IllegalStateException("SofaScore returned no seasons for tournament " + query.leagueId());

            SeasonDTO season = seasons.stream().filter(Objects::nonNull)
                    .filter(item -> SofaScoreSeasonMatcher.matches(query.season(), item)).findFirst()
                    .orElseThrow(() -> new IllegalStateException("SofaScore season '" + query.season()
                                                                 + "' was not found for tournament " + query.leagueId()));

            int maxPages = appProperties.getSync().getMaxEventPages() > 0
                    ? appProperties.getSync().getMaxEventPages() : 20;
            Map<String, ProviderFixture> fixtures = new LinkedHashMap<>();
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            // Scheduled fixture refreshes should not crawl every historical page.
            // The separate result-sync job owns recent score/status reconciliation.
            if (query.from() == null || query.from().isBefore(today)) {
                fetchDirection(query, season, "last", maxPages, page, fixtures);
            }
            if (query.to() == null || !query.to().isBefore(today)) {
                fetchDirection(query, season, "next", maxPages, page, fixtures);
            }
            log.info("SofaScore fixtures fetched tournament={} season={} count={} pagesPerDirection={}",
                    query.leagueId(), season.name(), fixtures.size(), maxPages);
            return List.copyOf(fixtures.values());
        });
    }

    private void fetchDirection(FixtureQuery query, SeasonDTO season,
                                String direction, int maxPages, Page page,
                                Map<String, ProviderFixture> fixtures) {
        for (int pageIndex = 0; pageIndex < maxPages; pageIndex++) {
            MatchesResponseDTO response = sofaScoreClient.fetchSeasonMatchesPage(
                    query.leagueId(), season.id(), direction, pageIndex, page);
            if (response.events() != null) response.events().stream().map(mapper::toProviderFixture)
                    .filter(Objects::nonNull).filter(f -> inDateRange(f, query.from(), query.to()))
                    .forEach(f -> fixtures.putIfAbsent(f.externalId(), f));
            if (!response.hasNextPage()) break;
        }
    }

    private boolean inDateRange(ProviderFixture fixture, LocalDate from, LocalDate to) {
        if (fixture.kickoff() == null) return from == null && to == null;
        LocalDate date = fixture.kickoff().atZone(ZoneOffset.UTC).toLocalDate();
        return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
    }
}
