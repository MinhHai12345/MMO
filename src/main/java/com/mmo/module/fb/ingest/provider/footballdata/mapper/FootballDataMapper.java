package com.mmo.module.fb.ingest.provider.footballdata.mapper;

import com.mmo.AppProperties;
import com.mmo.module.fb.league.enums.StandingType;
import com.mmo.module.fb.ingest.provider.footballdata.dto.FootballDataDtos.*;
import com.mmo.module.fb.ingest.provider.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class FootballDataMapper {

    private final AppProperties appProperties;

    public ProviderLeague toLeague(CompetitionDto competition) {
        if (competition == null || competition.id() == null) return null;

        SeasonDto currentSeason = competition.currentSeason();
        String currentYear = currentSeason == null || currentSeason.startDate() == null
                ? null : Integer.toString(currentSeason.startDate().getYear());

        List<String> configuredYears = appProperties.getFootballData() == null
                || appProperties.getFootballData().getSeasons() == null
                ? List.of()
                : appProperties.getFootballData().getSeasons().stream()
                    .filter(year -> year != null && !year.isBlank())
                    .map(String::trim)
                    .distinct()
                    .toList();

        List<ProviderSeason> seasons = new ArrayList<>();
        if (!configuredYears.isEmpty()) {
            for (String year : configuredYears) {
                boolean isCurrent = year.equals(currentYear);
                boolean hasCurrentMetadata = isCurrent && currentSeason != null;
                seasons.add(new ProviderSeason(
                        competition.id() + ":" + year,
                        year,
                        isCurrent,
                        hasCurrentMetadata ? currentSeason.startDate() : null,
                        hasCurrentMetadata ? currentSeason.endDate() : null));
            }
        } else if (currentYear != null) {
            seasons.add(new ProviderSeason(
                    competition.id() + ":" + currentYear,
                    currentYear,
                    true,
                    currentSeason.startDate(),
                    currentSeason.endDate()));
        }

        return new ProviderLeague(
                competition.id().toString(), competition.name(),
                competition.area() == null ? null : competition.area().name(),
                competition.emblem(), seasons);
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
        return new ProviderFixture(match.id().toString(), match.homeTeam().id().toString(),
                match.awayTeam().id().toString(), kickoff,
                match.matchday() == null ? match.stage() : "Matchday " + match.matchday(),
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
