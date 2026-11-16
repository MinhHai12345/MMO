package com.mmo.module.fb.ingest.application.service.masterdata;

import com.mmo.module.fb.domain.league.entity.League;
import com.mmo.module.fb.domain.league.entity.Season;
import com.mmo.module.fb.domain.league.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.domain.league.repository.LeagueRepository;
import com.mmo.module.fb.domain.league.repository.SeasonExternalIdRepository;
import com.mmo.module.fb.domain.league.repository.SeasonRepository;
import com.mmo.module.fb.domain.team.entity.Team;
import com.mmo.module.fb.domain.team.repository.TeamExternalIdRepository;
import com.mmo.module.fb.domain.team.repository.TeamRepository;
import com.mmo.module.fb.ingest.application.model.masterdata.MasterDataFile;
import com.mmo.module.fb.ingest.enums.Provider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exports the canonical MMO master data together with every provider identity
 * mapping currently persisted in the database.
 */
@Service
@RequiredArgsConstructor
public class MasterDataExportService {

    private final LeagueRepository leagueRepository;
    private final SeasonRepository seasonRepository;
    private final TeamRepository teamRepository;
    private final LeagueExternalIdRepository leagueExternalIdRepository;
    private final SeasonExternalIdRepository seasonExternalIdRepository;
    private final TeamExternalIdRepository teamExternalIdRepository;

    @Transactional(readOnly = true)
    public MasterDataFile export() {
        Map<Long, Map<String, String>> leagueMappings = new HashMap<>();
        Map<Long, Map<String, String>> leagueIdentifies = new HashMap<>();
        Map<Long, Map<String, String>> seasonMappings = new HashMap<>();
        Map<Long, Map<String, String>> teamMappings = new HashMap<>();

        for (Provider provider : Provider.values()) {
            leagueExternalIdRepository.findAllByProvider(provider).forEach(mapping -> {
                leagueMappings.computeIfAbsent(mapping.getLeague().getId(), ignored -> new LinkedHashMap<>())
                        .put(provider.name(), mapping.getExternalId());
                if (mapping.getIdentify() != null && !mapping.getIdentify().isBlank()) {
                    leagueIdentifies.computeIfAbsent(mapping.getLeague().getId(), ignored -> new LinkedHashMap<>())
                            .put(provider.name(), mapping.getIdentify());
                }
            });
            seasonExternalIdRepository.findAllByProvider(provider).forEach(mapping ->
                    seasonMappings.computeIfAbsent(mapping.getSeason().getId(), ignored -> new LinkedHashMap<>())
                            .put(provider.name(), mapping.getExternalId()));
            teamExternalIdRepository.findAllByProvider(provider).forEach(mapping ->
                    teamMappings.computeIfAbsent(mapping.getTeam().getId(), ignored -> new LinkedHashMap<>())
                            .put(provider.name(), mapping.getExternalId()));
        }

        Map<Long, List<MasterDataFile.SeasonEntry>> seasonsByLeague = new HashMap<>();
        for (Season season : seasonRepository.findAll()) {
            seasonsByLeague.computeIfAbsent(season.getLeague().getId(), ignored -> new ArrayList<>())
                    .add(new MasterDataFile.SeasonEntry(
                            season.getYear(),
                            season.isCurrent(),
                            season.isActive(),
                            season.getStartDate(),
                            season.getEndDate(),
                            immutableMappings(seasonMappings.get(season.getId()))));
        }

        List<MasterDataFile.LeagueEntry> leagues = leagueRepository.findAll().stream()
                .map(league -> toLeagueEntry(league, seasonsByLeague, leagueMappings, leagueIdentifies))
                .sorted(Comparator.comparing(MasterDataFile.LeagueEntry::code,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();

        List<MasterDataFile.TeamEntry> teams = teamRepository.findAll().stream()
                .map(team -> toTeamEntry(team, teamMappings))
                .sorted(Comparator.comparing(MasterDataFile.TeamEntry::code,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();

        return new MasterDataFile(leagues, teams);
    }

    private MasterDataFile.LeagueEntry toLeagueEntry(
            League league,
            Map<Long, List<MasterDataFile.SeasonEntry>> seasonsByLeague,
            Map<Long, Map<String, String>> mappings,
            Map<Long, Map<String, String>> identifies) {
        List<MasterDataFile.SeasonEntry> seasons = seasonsByLeague
                .getOrDefault(league.getId(), List.of()).stream()
                .sorted(Comparator.comparing(MasterDataFile.SeasonEntry::year,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
        return new MasterDataFile.LeagueEntry(
                league.getSlug(),
                league.getName(),
                league.getCountry(),
                league.getLogoUrl(),
                league.getIsActive(),
                league.getPopularityScore(),
                seasons,
                immutableMappings(mappings.get(league.getId())),
                immutableMappings(identifies.get(league.getId())));
    }

    private MasterDataFile.TeamEntry toTeamEntry(
            Team team, Map<Long, Map<String, String>> mappings) {
        return new MasterDataFile.TeamEntry(
                team.getSlug(),
                team.getName(),
                team.getShortName(),
                team.getLogoUrl(),
                team.getIsActive(),
                immutableMappings(mappings.get(team.getId())));
    }

    private Map<String, String> immutableMappings(Map<String, String> mappings) {
        return mappings == null ? Map.of() : Map.copyOf(mappings);
    }
}
