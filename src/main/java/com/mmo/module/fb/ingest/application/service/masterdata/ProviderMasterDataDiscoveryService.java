package com.mmo.module.fb.ingest.application.service.masterdata;

import com.mmo.module.fb.ingest.application.model.masterdata.ProviderMasterDataDiscoveryReport;
import com.mmo.module.fb.ingest.application.model.provider.ProviderLeague;
import com.mmo.module.fb.ingest.application.model.provider.ProviderSeason;
import com.mmo.module.fb.ingest.application.model.provider.SeasonQuery;
import com.mmo.module.fb.ingest.application.model.provider.TeamQuery;
import com.mmo.module.fb.ingest.application.port.out.provider.LeagueIngestionPort;
import com.mmo.module.fb.ingest.application.port.out.provider.SeasonIngestionPort;
import com.mmo.module.fb.ingest.application.port.out.provider.TeamIngestionPort;
import com.mmo.module.fb.ingest.enums.Provider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderMasterDataDiscoveryService {

    private final List<LeagueIngestionPort> leagueProviders;
    private final List<SeasonIngestionPort> seasonProviders;
    private final List<TeamIngestionPort> teamProviders;

    /**
     * Reads provider catalogues only. Team retrieval is opt-in and requires explicit
     * league-season queries to prevent an accidental full-catalogue fan-out.
     */
    public ProviderMasterDataDiscoveryReport discover(DiscoveryRequest request) {
        Set<Provider> selected = request == null || request.providers() == null
                || request.providers().isEmpty()
                ? EnumSet.allOf(Provider.class) : EnumSet.copyOf(request.providers());

        List<ProviderMasterDataDiscoveryReport.ProviderResult> results = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();

        for (Provider provider : selected.stream().sorted().toList()) {
            List<String> warnings = new ArrayList<>();
            List<ProviderLeague> leagues = List.of();
            List<ProviderSeason> seasons = new ArrayList<>();
            List<com.mmo.module.fb.ingest.application.model.provider.ProviderTeam> teams = new ArrayList<>();
            String error = null;
            boolean supported = false;

            try {
                LeagueIngestionPort leaguePort = leagueProviders.stream()
                        .filter(port -> port.getProvider() == provider).findFirst().orElse(null);
                if (leaguePort != null) {
                    supported = true;
                    leagues = safe(leaguePort.fetchLeagues(request == null ? null : request.externalLeagueIds()));
                    for (ProviderLeague league : leagues) {
                        if (league == null) continue;
                        seasons.addAll(safe(league.seasons()));
                        SeasonIngestionPort seasonPort = seasonProviders.stream()
                                .filter(port -> port.getProvider() == provider).findFirst().orElse(null);
                        if (seasonPort != null) {
                            List<ProviderSeason> discovered = seasonPort.fetchSeasons(
                                    new SeasonQuery(league.externalId(), league.identify()));
                            seasons.addAll(safe(discovered));
                        }
                    }
                }

                List<ProviderMasterDataDiscoveryReport.TeamQuery> requestedTeamQueries =
                        request == null || request.teamQueries() == null ? List.of()
                                : request.teamQueries().stream()
                                .filter(query -> query != null && query.provider() == provider).toList();
                TeamIngestionPort teamPort = teamProviders.stream()
                        .filter(port -> port.getProvider() == provider).findFirst().orElse(null);
                if (!requestedTeamQueries.isEmpty()) {
                    if (teamPort == null) {
                        warnings.add("Team catalogue retrieval is not supported by this provider.");
                    } else {
                        supported = true;
                        for (var query : requestedTeamQueries) {
                            teams.addAll(safe(teamPort.fetchTeams(new TeamQuery(
                                    query.leagueId(), query.season(), query.leagueIdentify()))));
                        }
                    }
                }

                if (leaguePort == null) {
                    warnings.add("League catalogue retrieval is not supported by this provider.");
                }
                if (seasonProviders.stream().noneMatch(port -> port.getProvider() == provider)
                        && leagues.stream().noneMatch(league -> league.seasons() != null && !league.seasons().isEmpty())) {
                    warnings.add("No season catalogue capability or embedded season data is available.");
                }
                if (teamPort == null) {
                    warnings.add("Team catalogue retrieval is not supported by this provider.");
                }
            } catch (Exception exception) {
                error = exception.getClass().getSimpleName() + ": " + exception.getMessage();
                log.warn("Provider master data discovery failed: provider={}", provider, exception);
            }

            seasons = distinctSeasons(seasons, provider, conflicts);
            leagues = distinctLeagues(leagues, provider, conflicts);
            teams = distinctTeams(teams, provider, conflicts);
            String status = error != null ? "FAILED" : !supported ? "UNSUPPORTED"
                    : warnings.isEmpty() ? "SUCCESS" : "PARTIAL";
            results.add(new ProviderMasterDataDiscoveryReport.ProviderResult(
                    provider, status, leagues, List.copyOf(seasons), List.copyOf(teams),
                    List.copyOf(warnings), error));
        }

        String status = results.stream().anyMatch(result -> "FAILED".equals(result.status())) ? "PARTIAL"
                : results.stream().allMatch(result -> "UNSUPPORTED".equals(result.status())) ? "UNSUPPORTED"
                : conflicts.isEmpty() ? "COMPLETED" : "COMPLETED_WITH_CONFLICTS";
        return new ProviderMasterDataDiscoveryReport(status, List.copyOf(results), List.copyOf(conflicts));
    }

    private List<ProviderLeague> distinctLeagues(List<ProviderLeague> values, Provider provider,
                                                  List<String> conflicts) {
        Map<String, ProviderLeague> unique = new HashMap<>();
        for (ProviderLeague value : values) {
            if (value == null || blank(value.externalId())) continue;
            ProviderLeague previous = unique.putIfAbsent(value.externalId(), value);
            if (previous != null && !Objects.equals(previous.name(), value.name())) {
                conflicts.add(provider + " league externalId=" + value.externalId()
                        + " has conflicting names: " + previous.name() + " / " + value.name());
            }
        }
        return unique.values().stream().sorted(Comparator.comparing(ProviderLeague::externalId)).toList();
    }

    private List<ProviderSeason> distinctSeasons(List<ProviderSeason> values, Provider provider,
                                                  List<String> conflicts) {
        Map<String, ProviderSeason> unique = new HashMap<>();
        for (ProviderSeason value : values) {
            if (value == null || blank(value.externalId())) continue;
            ProviderSeason previous = unique.putIfAbsent(value.externalId(), value);
            if (previous != null && !Objects.equals(previous.year(), value.year())) {
                conflicts.add(provider + " season externalId=" + value.externalId()
                        + " has conflicting years: " + previous.year() + " / " + value.year());
            }
        }
        return unique.values().stream().sorted(Comparator.comparing(ProviderSeason::year,
                Comparator.nullsLast(String::compareTo))).toList();
    }

    private List<com.mmo.module.fb.ingest.application.model.provider.ProviderTeam> distinctTeams(
            List<com.mmo.module.fb.ingest.application.model.provider.ProviderTeam> values,
            Provider provider, List<String> conflicts) {
        Map<String, com.mmo.module.fb.ingest.application.model.provider.ProviderTeam> unique = new HashMap<>();
        for (var value : values) {
            if (value == null || blank(value.externalId())) continue;
            var previous = unique.putIfAbsent(value.externalId(), value);
            if (previous != null && !Objects.equals(previous.name(), value.name())) {
                conflicts.add(provider + " team externalId=" + value.externalId()
                        + " has conflicting names: " + previous.name() + " / " + value.name());
            }
        }
        return unique.values().stream().sorted(Comparator.comparing(
                com.mmo.module.fb.ingest.application.model.provider.ProviderTeam::externalId)).toList();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    public record DiscoveryRequest(
            List<Provider> providers,
            List<Long> externalLeagueIds,
            List<ProviderMasterDataDiscoveryReport.TeamQuery> teamQueries) { }
}
