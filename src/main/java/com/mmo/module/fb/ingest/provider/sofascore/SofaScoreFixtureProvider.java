package com.mmo.module.fb.ingest.provider.sofascore;

import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.model.FixtureQuery;
import com.mmo.module.fb.ingest.provider.model.ProviderFixture;
import com.mmo.module.fb.ingest.provider.model.sofa.SofaMatchesData;
import com.mmo.module.fb.ingest.provider.model.sofa.SofaSeasonData;
import com.mmo.module.fb.ingest.provider.sofascore.client.SofaScoreClient;
import com.mmo.module.fb.ingest.provider.sofascore.mapper.SofaScoreFixtureMapper;
import com.mmo.module.fb.ingest.provider.spi.FixtureProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
            List<SofaSeasonData.SeasonDTO> seasons =
                    sofaScoreClient.fetchSeasonByTournamentId(query.leagueId(), page);
            if (seasons == null) return List.of();
            SofaSeasonData.SeasonDTO season = seasons.stream()
                    .filter(Objects::nonNull)
                    .filter(item -> query.season() == null || query.season().equals(item.getYear())
                                    || query.season().equals(item.getName()))
                    .findFirst().orElse(null);
            if (season == null || season.getId() == null) return List.of();

            List<ProviderFixture> fixtures = new ArrayList<>();
            int emptyRounds = 0;
            int maxRounds = appProperties.getSync().getMaxRounds() > 0
                    ? appProperties.getSync().getMaxRounds() : 50;
            for (int round = 1; round <= maxRounds; round++) {
                List<SofaMatchesData.SofaEventDTO> events =
                        sofaCrawlerService.fetchMatchesByRound(query.leagueId(), season.getId(), round, page);
                if (events == null || events.isEmpty()) {
                    emptyRounds++;
                    if (emptyRounds >= 4 && round > 10) break;
                    continue;
                }
                emptyRounds = 0;
                events.stream()
                        .map(mapper::toProviderFixture)
                        .filter(Objects::nonNull)
                        .filter(fixture -> inDateRange(fixture, query.from(), query.to()))
                        .forEach(fixtures::add);
            }
            return fixtures.stream().collect(Collectors.toMap(
                    ProviderFixture::externalId, fixture -> fixture, (first, ignored) -> first,
                    LinkedHashMap::new)).values().stream().toList();
        });
    }

    private boolean inDateRange(ProviderFixture fixture, LocalDate from, LocalDate to) {
        if (fixture.kickoff() == null) return from == null && to == null;
        LocalDate date = fixture.kickoff().atZone(ZoneOffset.UTC).toLocalDate();
        return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
    }
}
