package com.mmo.module.fb.ingest.provider.sofascore;

import com.microsoft.playwright.Page;
import com.mmo.module.fb.ingest.provider.model.FixtureQuery;
import com.mmo.module.fb.ingest.provider.model.ProviderFixture;
import com.mmo.module.fb.ingest.provider.model.sofa.SofaMatchesData;
import com.mmo.module.fb.ingest.provider.model.sofa.SofaSeasonData;
import com.mmo.module.fb.ingest.provider.service.SofaCrawlerService;
import com.mmo.module.fb.ingest.provider.spi.FixtureProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class SofaScoreFixtureProvider extends AbstractSofaScoreProvider implements FixtureProvider {

    public SofaScoreFixtureProvider(SofaCrawlerService sofaCrawlerService) {
        super(sofaCrawlerService);
    }

    @Override
    public List<ProviderFixture> fetchFixtures(FixtureQuery query) {
        if (query == null || query.leagueId() == null) return List.of();
        return withPage(page -> {
            List<SofaSeasonData.SeasonDTO> seasons =
                    sofaCrawlerService.fetchSeasonByTournamentId(query.leagueId(), page);
            if (seasons == null) return List.of();
            SofaSeasonData.SeasonDTO season = seasons.stream().filter(Objects::nonNull)
                    .filter(s -> query.season() == null || query.season().equals(s.getYear())
                            || query.season().equals(s.getName()))
                    .findFirst().orElse(null);
            if (season == null || season.getId() == null) return List.of();

            List<ProviderFixture> fixtures = new ArrayList<>();
            int maxRounds = 50;
            int emptyRounds = 0;
            for (int round = 1; round <= maxRounds; round++) {
                List<SofaMatchesData.SofaEventDTO> events =
                        sofaCrawlerService.fetchMatchesByRound(query.leagueId(), season.getId(), round, page);
                if (events == null || events.isEmpty()) {
                    emptyRounds++;
                    if (emptyRounds >= 4 && round > 10) break;
                    continue;
                }
                emptyRounds = 0;
                events.stream().filter(Objects::nonNull)
                        .map(this::toFixture)
                        .filter(Objects::nonNull)
                        .filter(f -> inDateRange(f, query.from(), query.to()))
                        .forEach(fixtures::add);
            }
            return fixtures.stream().collect(java.util.stream.Collectors.toMap(
                    ProviderFixture::externalId, fixture -> fixture, (first, ignored) -> first,
                    java.util.LinkedHashMap::new)).values().stream().toList();
        });
    }

    private ProviderFixture toFixture(SofaMatchesData.SofaEventDTO event) {
        if (event.getId() == null || event.getHomeTeam() == null || event.getAwayTeam() == null
                || event.getHomeTeam().getId() == null || event.getAwayTeam().getId() == null) return null;
        Instant kickoff = event.getStartTimestamp() == null ? null
                : Instant.ofEpochSecond(event.getStartTimestamp());
        String status = event.getStatus() == null ? null : mapStatus(event.getStatus().getType());
        return new ProviderFixture(event.getId().toString(),
                event.getHomeTeam().getId().toString(), event.getAwayTeam().getId().toString(),
                kickoff, event.getStartTimestamp(),
                event.getRoundInfo() == null || event.getRoundInfo().getRound() == null
                        ? null : event.getRoundInfo().getRound().toString(),
                null, null, status,
                event.getHomeScore() == null ? null : event.getHomeScore().getCurrent(),
                event.getAwayScore() == null ? null : event.getAwayScore().getCurrent());
    }

    private String mapStatus(String status) {
        if (status == null) return "NS";
        return switch (status.toLowerCase()) {
            case "finished" -> "FT";
            case "inprogress", "live" -> "LIVE";
            case "postponed" -> "PST";
            case "canceled", "cancelled" -> "CANC";
            default -> "NS";
        };
    }

    private boolean inDateRange(ProviderFixture fixture, LocalDate from, LocalDate to) {
        if (fixture.kickoff() == null) return from == null && to == null;
        LocalDate date = fixture.kickoff().atZone(java.time.ZoneOffset.UTC).toLocalDate();
        return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
    }
}
