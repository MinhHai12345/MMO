package com.mmo.module.fb.ingest.provider.footballdata.mapper;

import com.mmo.AppProperties;
import com.mmo.module.fb.league.enums.StandingType;
import com.mmo.module.fb.ingest.provider.footballdata.dto.FootballDataDtos.*;
import com.mmo.module.fb.ingest.provider.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class FootballDataMapper {

    private final AppProperties appProperties;

    public ProviderLeague toLeague(CompetitionDto competition) {
        if (competition == null || competition.id() == null) return null;

        SeasonDto currentSeason = competition.currentSeason();
        String currentYear = seasonYear(currentSeason);

        AppProperties.ProviderConfig footballDataConfig = appProperties.getIngestion().getProviderConfig()
                .get(com.mmo.module.fb.ingest.enums.Provider.FOOTBALL_DATA);
        List<String> configuredYears = footballDataConfig == null || footballDataConfig.getSeasons() == null
                ? List.of()
                : footballDataConfig.getSeasons().stream()
                    .filter(year -> year != null && !year.isBlank())
                    .map(String::trim)
                    .distinct()
                    .toList();

        // football-data.org returns the season catalogue on the competition resource.
        // Keep its real season IDs and dates; synthesizing IDs for every configured year
        // creates false external mappings for seasons the provider did not actually return.
        Map<String, SeasonDto> availableSeasonsByYear = new LinkedHashMap<>();
        if (competition.seasons() != null) {
            for (SeasonDto season : competition.seasons()) {
                String year = seasonYear(season);
                if (year != null) availableSeasonsByYear.putIfAbsent(year, season);
            }
        }
        if (currentSeason != null && currentYear != null) {
            availableSeasonsByYear.putIfAbsent(currentYear, currentSeason);
        }

        List<ProviderSeason> seasons = new ArrayList<>();
        for (Map.Entry<String, SeasonDto> entry : availableSeasonsByYear.entrySet()) {
            String year = entry.getKey();
            SeasonDto season = entry.getValue();
            if (!configuredYears.isEmpty() && !configuredYears.contains(year)) continue;
            // Without an explicit backfill configuration, retain the existing current-season
            // default. With one, only map seasons actually present in the provider catalogue.
            if (configuredYears.isEmpty() && !year.equals(currentYear)) continue;

            String externalSeasonId = season.id() == null
                    ? competition.id() + ":" + year
                    : season.id().toString();
            seasons.add(new ProviderSeason(externalSeasonId, year, year.equals(currentYear),
                    season.startDate(), season.endDate()));
        }

        return new ProviderLeague(
                competition.id().toString(), competition.name(),
                competition.area() == null ? null : competition.area().name(),
                competition.emblem(), seasons);
    }

    private static String seasonYear(SeasonDto season) {
        return season == null || season.startDate() == null
                ? null : Integer.toString(season.startDate().getYear());
    }

    public ProviderTeam toTeam(TeamDto team) {
        if (team == null || team.id() == null) return null;
        return new ProviderTeam(team.id().toString(), team.name(),
                team.tla() != null ? team.tla() : team.shortName(),
                team.crest(), team.founded(), team.venue(), null);
    }

    public ProviderFixture toFixture(MatchDto match) {
        if (match == null || match.id() == null || match.homeTeam() == null
                || match.homeTeam().id() == null || match.awayTeam() == null
                || match.awayTeam().id() == null) return null;
        Instant kickoff = match.utcDate() == null ? null : match.utcDate().toInstant();
        ScorePairDto score = match.score() == null ? null
                : (match.score().fullTime() != null ? match.score().fullTime() : match.score().regularTime());
        String round = match.matchday() == null ? match.stage() : "Matchday " + match.matchday();
        return new ProviderFixture(match.id().toString(), match.homeTeam().id().toString(),
                match.awayTeam().id().toString(), kickoff, round,
                match.venue(), null, match.status(),
                score == null ? null : score.home(), score == null ? null : score.away());
    }

    public List<ProviderStanding> toStandings(StandingsDto response) {
        List<ProviderStanding> result = new ArrayList<>();
        if (response == null || response.standings() == null) return result;
        for (StandingTableDto table : response.standings()) {
            if (table == null || table.table() == null) continue;
            StandingType type = StandingType.fromProviderValue(table.type());
            for (StandingRowDto row : table.table()) {
                if (row == null || row.team() == null || row.team().id() == null) continue;
                result.add(new ProviderStanding(row.team().id().toString(), row.position(),
                        row.points(), row.goalDifference(), row.form(),
                        row.group() != null ? row.group() : table.group(),
                        row.playedGames(), row.won(), row.draw(), row.lost(),
                        row.goalsFor(), row.goalsAgainst(), type));
            }
        }
        return result;
    }
}
