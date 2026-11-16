package com.mmo.module.fb.ingest.application.service.masterdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmo.module.fb.domain.league.entity.League;
import com.mmo.module.fb.domain.league.entity.Season;
import com.mmo.module.fb.domain.league.repository.LeagueRepository;
import com.mmo.module.fb.domain.league.repository.SeasonRepository;
import com.mmo.module.fb.domain.team.entity.Team;
import com.mmo.module.fb.domain.team.repository.TeamRepository;
import com.mmo.module.fb.ingest.application.model.masterdata.MasterDataFile;
import com.mmo.module.fb.ingest.application.model.masterdata.MasterDataInitializationReport;
import com.mmo.module.fb.ingest.application.port.out.ExternalIdMappingPort;
import com.mmo.module.fb.ingest.enums.Provider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class MasterDataInitializationService {

    private static final String MASTER_DATA_PATH = "master-data/football-master-data.json";

    private final ObjectMapper objectMapper;
    private final LeagueRepository leagueRepository;
    private final SeasonRepository seasonRepository;
    private final TeamRepository teamRepository;
    private final ExternalIdMappingPort externalIdMappingPort;

    @Transactional(readOnly = true)
    public MasterDataInitializationReport preview() {
        return inspect(loadMasterData(), "DRY_RUN");
    }

    @Transactional
    public MasterDataInitializationReport apply() {
        MasterDataFile data = loadMasterData();
        MasterDataInitializationReport preview = inspect(data, "APPLY");
        if (!preview.valid()) {
            throw new IllegalArgumentException("Master data validation failed: "
                    + String.join("; ", preview.validationErrors()));
        }

        for (MasterDataFile.LeagueEntry entry : safe(data.leagues())) {
            League league = resolveLeague(entry, true);
            registerMappings(entry.externalMappings(), (provider, externalId) -> {
                var mapping = externalIdMappingPort.registerLeague(league, provider, externalId);
                if (blank(mapping.getIdentify())) {
                    mapping.setIdentify(externalId);
                }
            });

            for (MasterDataFile.SeasonEntry seasonEntry : safe(entry.seasons())) {
                Season season = seasonRepository.findByLeague_IdAndYear(league.getId(), seasonEntry.year())
                        .orElseGet(() -> seasonRepository.save(Season.builder()
                                .league(league)
                                .year(seasonEntry.year())
                                .isCurrent(Boolean.TRUE.equals(seasonEntry.current()))
                                .isActive(Boolean.TRUE.equals(seasonEntry.active()))
                                .startDate(seasonEntry.startDate())
                                .endDate(seasonEntry.endDate())
                                .build()));
                registerMappings(seasonEntry.externalMappings(), (provider, externalId) ->
                        externalIdMappingPort.registerSeason(season, provider, externalId));
            }
        }

        for (MasterDataFile.TeamEntry entry : safe(data.teams())) {
            Team team = resolveTeam(entry, true);
            registerMappings(entry.externalMappings(), (provider, externalId) ->
                    externalIdMappingPort.registerTeam(team, provider, externalId));
        }

        log.info("Master data initialization applied: leagues={}, teams={}",
                safe(data.leagues()).size(), safe(data.teams()).size());
        return previewWithAppliedCounts(preview);
    }

    private MasterDataInitializationReport previewWithAppliedCounts(MasterDataInitializationReport report) {
        return new MasterDataInitializationReport("APPLIED",
                0, report.leaguesToCreate() + report.leaguesExisting(),
                0, report.seasonsToCreate() + report.seasonsExisting(),
                0, report.teamsToCreate() + report.teamsExisting(),
                report.mappingsToRegister(), List.of());
    }

    private MasterDataInitializationReport inspect(MasterDataFile data, String mode) {
        List<String> errors = validate(data);
        if (data == null) {
            return new MasterDataInitializationReport(mode, 0, 0, 0, 0, 0, 0, 0, errors);
        }
        int newLeagues = 0, existingLeagues = 0, newSeasons = 0, existingSeasons = 0;
        int newTeams = 0, existingTeams = 0, mappings = 0;

        for (MasterDataFile.LeagueEntry entry : safe(data.leagues())) {
            League existing = findLeague(entry.code());
            if (existing == null) newLeagues++; else existingLeagues++;
            mappings += mappingCount(entry.externalMappings());
            for (MasterDataFile.SeasonEntry season : safe(entry.seasons())) {
                if (existing != null && seasonRepository.findByLeague_IdAndYear(existing.getId(), season.year()).isPresent()) {
                    existingSeasons++;
                } else {
                    newSeasons++;
                }
                mappings += mappingCount(season.externalMappings());
            }
        }
        for (MasterDataFile.TeamEntry entry : safe(data.teams())) {
            if (findTeam(entry.code()) == null) newTeams++; else existingTeams++;
            mappings += mappingCount(entry.externalMappings());
        }
        return new MasterDataInitializationReport(mode, newLeagues, existingLeagues,
                newSeasons, existingSeasons, newTeams, existingTeams, mappings, errors);
    }

    private List<String> validate(MasterDataFile data) {
        List<String> errors = new ArrayList<>();
        if (data == null) return List.of("Master data document is empty");
        Set<String> leagueCodes = new HashSet<>();
        Set<String> teamCodes = new HashSet<>();
        Set<String> leagueExternalIds = new HashSet<>();
        Set<String> seasonExternalIds = new HashSet<>();
        Set<String> teamExternalIds = new HashSet<>();
        for (MasterDataFile.LeagueEntry league : safe(data.leagues())) {
            if (blank(league.code())) errors.add("League code is required");
            else if (!leagueCodes.add(league.code().trim().toLowerCase(Locale.ROOT))) errors.add("Duplicate league code: " + league.code());
            if (blank(league.name())) {
                errors.add("League name is required: " + league.code());
            } else if (!blank(league.code()) && findLeague(league.code()) == null
                    && !leagueRepository.findAllByNameIgnoreCase(league.name().trim()).isEmpty()) {
                errors.add("League name already exists with a different code: " + league.name());
            }
            validateMappings(league.externalMappings(), "league " + league.code(), errors);
            validateUniqueMappingIds(league.externalMappings(), leagueExternalIds, "league " + league.code(), errors);
            Set<String> years = new HashSet<>();
            for (MasterDataFile.SeasonEntry season : safe(league.seasons())) {
                if (blank(season.year())) errors.add("Season year is required: " + league.code());
                else if (!years.add(season.year().trim())) errors.add("Duplicate season " + season.year() + " in " + league.code());
                if (season.startDate() != null && season.endDate() != null && season.endDate().isBefore(season.startDate())) {
                    errors.add("Season endDate precedes startDate: " + league.code() + "/" + season.year());
                }
                validateMappings(season.externalMappings(), "season " + league.code() + "/" + season.year(), errors);
                validateUniqueMappingIds(season.externalMappings(), seasonExternalIds,
                        "season " + league.code() + "/" + season.year(), errors);
            }
        }
        for (MasterDataFile.TeamEntry team : safe(data.teams())) {
            if (blank(team.code())) errors.add("Team code is required");
            else if (!teamCodes.add(team.code().trim().toLowerCase(Locale.ROOT))) errors.add("Duplicate team code: " + team.code());
            if (blank(team.name())) {
                errors.add("Team name is required: " + team.code());
            } else if (!blank(team.code()) && findTeam(team.code()) == null
                    && !teamRepository.findAllByNameIgnoreCase(team.name().trim()).isEmpty()) {
                errors.add("Team name already exists with a different code: " + team.name());
            }
            validateMappings(team.externalMappings(), "team " + team.code(), errors);
            validateUniqueMappingIds(team.externalMappings(), teamExternalIds, "team " + team.code(), errors);
        }
        validateExistingMappings(data, errors);
        return errors;
    }

    private void validateExistingMappings(MasterDataFile data, List<String> errors) {
        for (MasterDataFile.LeagueEntry entry : safe(data.leagues())) {
            League league = findLeague(entry.code());
            validateLeagueMappings(league, entry.externalMappings(), "league " + entry.code(), errors);
            for (MasterDataFile.SeasonEntry seasonEntry : safe(entry.seasons())) {
                Season season = league == null ? null : seasonRepository
                        .findByLeague_IdAndYear(league.getId(), seasonEntry.year()).orElse(null);
                Map<String, String> mappings = seasonEntry.externalMappings();
                if (mappings == null) continue;
                mappings.forEach((providerName, externalId) -> {
                    if (blank(providerName) || blank(externalId)) return;
                    Provider provider = parseProvider(providerName);
                    if (provider == null) return;
                    Season mapped = externalIdMappingPort.findSeason(provider, externalId.trim()).orElse(null);
                    if (mapped != null && (season == null || !sameEntity(mapped, season))) {
                        errors.add("Season external ID is already assigned to another canonical season: "
                                + provider + "/" + externalId);
                    }
                });
            }
        }
        for (MasterDataFile.TeamEntry entry : safe(data.teams())) {
            Team team = findTeam(entry.code());
            Map<String, String> mappings = entry.externalMappings();
            if (mappings == null) continue;
            mappings.forEach((providerName, externalId) -> {
                if (blank(providerName) || blank(externalId)) return;
                Provider provider = parseProvider(providerName);
                if (provider == null) return;
                Team mapped = externalIdMappingPort.findTeam(provider, externalId.trim()).orElse(null);
                if (mapped != null && (team == null || !sameEntity(mapped, team))) {
                    errors.add("Team external ID is already assigned to another canonical team: "
                            + provider + "/" + externalId);
                }
            });
        }
    }

    private void validateLeagueMappings(League league, Map<String, String> mappings,
                                        String owner, List<String> errors) {
        if (mappings == null) return;
        mappings.forEach((providerName, externalId) -> {
            if (blank(providerName) || blank(externalId)) return;
            Provider provider = parseProvider(providerName);
            if (provider == null) return;
            League mapped = externalIdMappingPort.findLeague(provider, externalId.trim()).orElse(null);
            if (mapped != null && (league == null || !sameEntity(mapped, league))) {
                errors.add("League external ID is already assigned to another canonical league: "
                        + provider + "/" + externalId + " in " + owner);
            }
        });
    }

    private boolean sameEntity(com.mmo.core.entity.AbstractEntity left,
                               com.mmo.core.entity.AbstractEntity right) {
        return left != null && right != null && left.getId() != null
                && left.getId().equals(right.getId());
    }

    private Provider parseProvider(String providerName) {
        try {
            return Provider.valueOf(providerName.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private void validateUniqueMappingIds(Map<String, String> mappings, Set<String> seen,
                                          String owner, List<String> errors) {
        if (mappings == null) return;
        mappings.forEach((providerName, externalId) -> {
            if (blank(providerName) || blank(externalId)) return;
            Provider provider = parseProvider(providerName);
            if (provider == null) return;
            String key = provider.name() + ":" + externalId.trim();
            if (!seen.add(key)) {
                errors.add("Duplicate provider external ID " + key + " in " + owner);
            }
        });
    }

    private void validateMappings(Map<String, String> mappings, String owner, List<String> errors) {
        if (mappings == null) return;
        mappings.forEach((providerName, externalId) -> {
            try {
                Provider.valueOf(providerName.trim().toUpperCase(Locale.ROOT));
            } catch (RuntimeException ex) {
                errors.add("Unknown provider '" + providerName + "' in " + owner);
            }
            if (blank(externalId)) errors.add("Blank external ID for provider " + providerName + " in " + owner);
        });
    }

    private League resolveLeague(MasterDataFile.LeagueEntry entry, boolean create) {
        League existing = findLeague(entry.code());
        if (existing != null) return existing;
        List<League> sameName = leagueRepository.findAllByNameIgnoreCase(entry.name().trim());
        if (!sameName.isEmpty()) {
            throw new IllegalStateException("League name already exists with a different code: " + entry.name());
        }
        if (!create) return null;
        return leagueRepository.save(League.builder()
                .name(entry.name().trim())
                .slug(entry.code().trim())
                .country(entry.country())
                .logoUrl(entry.logoUrl())
                .isActive(entry.active() == null ? Boolean.FALSE : entry.active())
                .popularityScore(entry.popularityScore() == null ? BigDecimal.ZERO : entry.popularityScore())
                .build());
    }

    private League findLeague(String code) {
        if (blank(code)) return null;
        return leagueRepository.findBySlugIgnoreCase(code.trim()).orElse(null);
    }

    private Team resolveTeam(MasterDataFile.TeamEntry entry, boolean create) {
        Team existing = findTeam(entry.code());
        if (existing != null) return existing;
        List<Team> sameName = teamRepository.findAllByNameIgnoreCase(entry.name().trim());
        if (!sameName.isEmpty()) {
            throw new IllegalStateException("Team name already exists with a different code: " + entry.name());
        }
        if (!create) return null;
        return teamRepository.save(Team.builder()
                .name(entry.name().trim())
                .slug(entry.code().trim())
                .shortName(entry.shortName())
                .logoUrl(entry.logoUrl())
                .isActive(entry.active() == null ? Boolean.TRUE : entry.active())
                .build());
    }

    private Team findTeam(String code) {
        if (blank(code)) return null;
        List<Team> matches = teamRepository.findAllBySlugIgnoreCase(code.trim());
        if (matches.size() > 1) throw new IllegalStateException("Ambiguous canonical team code: " + code);
        return matches.isEmpty() ? null : matches.get(0);
    }

    private void registerMappings(Map<String, String> mappings, MappingRegistrar registrar) {
        if (mappings == null) return;
        mappings.forEach((providerName, externalId) ->
                registrar.register(Provider.valueOf(providerName.trim().toUpperCase(Locale.ROOT)), externalId.trim()));
    }

    private int mappingCount(Map<String, String> mappings) {
        return mappings == null ? 0 : mappings.size();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private MasterDataFile loadMasterData() {
        try (var inputStream = new ClassPathResource(MASTER_DATA_PATH).getInputStream()) {
            return objectMapper.readValue(inputStream, MasterDataFile.class);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read classpath:" + MASTER_DATA_PATH, ex);
        }
    }

    @FunctionalInterface
    private interface MappingRegistrar {
        void register(Provider provider, String externalId);
    }
}
