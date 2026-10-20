package com.mmo.module.fb.ingest.provider.footballdata.mapper;

import com.mmo.module.fb.ingest.provider.footballdata.dto.FootballDataDtos.*;
import com.mmo.module.fb.ingest.provider.model.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class FootballDataMapper {
    public ProviderLeague toLeague(CompetitionDto competition) {
        if (competition == null || competition.id() == null) return null;
        List<ProviderSeason> seasons = new ArrayList<>();
        SeasonDto season = competition.currentSeason();
        if (season != null && season.startDate() != null) {
            String year = Integer.toString(season.startDate().getYear());
            seasons.add(new ProviderSeason(
                    competition.id() + ":" + year, year, true, season.startDate(), season.endDate()));
        }
        return new ProviderLeague(
                competition.id().toString(),
                competition.name(),
                competition.area() == null ? null : competition.area().name(),
                competition.emblem(),
                seasons);
    }

    public ProviderTeam toTeam(TeamDto team) {
        if (team == null || team.id() == null) return null;
        return new ProviderTeam(
                team.id().toString(), team.name(),
                team.tla() != null ? team.tla() : team.shortName(),
                team.crest(), team.founded(),
                team.venue(), null);
    }

    public ProviderFixture toFixture(MatchDto match) {
        if (match == null || match.id() == null || match.homeTeam() == null
                || match.homeTeam().id() == null || match.awayTeam() == null
                || match.awayTeam().id() == null) return null;
        Instant kickoff = match.utcDate() == null ? null : match.utcDate().toInstant();
        ScorePairDto score = match.score() == null ? null
                : (match.score().fullTime() != null ? match.score().fullTime() : match.score().regularTime());
        return new ProviderFixture(
                match.id().toString(), match.homeTeam().id().toString(), match.awayTeam().id().toString(),
                kickoff, kickoff == null ? null : kickoff.getEpochSecond(),
                match.matchday() == null ? match.stage() : "Matchday " + match.matchday(),
                match.venue(), null, match.status(),
                score == null ? null : score.home(), score == null ? null : score.away());
    }

    public List<ProviderStanding> toStandings(StandingsDto response) {
        List<ProviderStanding> result = new ArrayList<>();
        if (response == null || response.standings() == null) return result;
        for (StandingTableDto table : response.standings()) {
            if (table == null || table.table() == null) continue;
            for (StandingRowDto row : table.table()) {
                if (row == null || row.team() == null || row.team().id() == null) continue;
                result.add(new ProviderStanding(
                        row.team().id().toString(), row.position(), row.points(), row.goalDifference(),
                        row.form(), row.group() != null ? row.group() : table.group(),
                        row.playedGames(), row.won(), row.draw(), row.lost(),
                        row.goalsFor(), row.goalsAgainst()));
            }
        }
        return result;
    }
}