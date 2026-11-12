package com.mmo.module.fb.ingest.application.service.integration;

import com.mmo.module.fb.ingest.application.model.SofaScoreDiagnosticRequest;
import com.mmo.module.fb.ingest.application.model.SofaScoreDiagnosticResult;
import com.mmo.module.fb.ingest.application.model.provider.FixtureQuery;
import com.mmo.module.fb.ingest.application.model.provider.ProviderLeague;
import com.mmo.module.fb.ingest.application.model.provider.ProviderLineups;
import com.mmo.module.fb.ingest.application.model.provider.ProviderSeason;
import com.mmo.module.fb.ingest.application.model.provider.TeamQuery;
import com.mmo.module.fb.ingest.application.port.in.SofaScoreDiagnosticUseCase;
import com.mmo.module.fb.ingest.application.port.out.provider.FixtureIngestionPort;
import com.mmo.module.fb.ingest.application.port.out.provider.LeagueIngestionPort;
import com.mmo.module.fb.ingest.application.port.out.provider.PlayerLineupsIngestionPort;
import com.mmo.module.fb.ingest.application.port.out.provider.TeamIngestionPort;
import com.mmo.module.fb.ingest.enums.Provider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.function.Predicate;

/**
 * Executes bounded, read-only checks against SofaScore outbound provider ports.
 * This service deliberately does not call ingestion use cases or persistence ports.
 */
@Service
@RequiredArgsConstructor
public class SofaScoreDiagnosticService implements SofaScoreDiagnosticUseCase {

    private static final Provider TARGET_PROVIDER = Provider.SOFA_SCORE;
    private static final int MAX_DATE_RANGE_DAYS = 31;

    private final List<LeagueIngestionPort> leagueProviders;
    private final List<TeamIngestionPort> teamProviders;
    private final List<FixtureIngestionPort> fixtureProviders;
    private final List<PlayerLineupsIngestionPort> lineupsProviders;

    @Override
    public SofaScoreDiagnosticResult diagnose(SofaScoreDiagnosticRequest request) {
        validate(request);

        List<SofaScoreDiagnosticResult.StageResult> stages = new ArrayList<>();
        LeagueIngestionPort leagueProvider = findProvider(leagueProviders, port -> port.getProvider() == TARGET_PROVIDER);
        TeamIngestionPort teamProvider = findProvider(teamProviders, port -> port.getProvider() == TARGET_PROVIDER);
        FixtureIngestionPort fixtureProvider = findProvider(fixtureProviders, port -> port.getProvider() == TARGET_PROVIDER);
        PlayerLineupsIngestionPort lineupsProvider = findProvider(
                lineupsProviders, port -> port.getProvider() == TARGET_PROVIDER);

        Outcome<List<ProviderLeague>> leagueOutcome = execute(
                "TOURNAMENT_AND_SEASONS",
                () -> leagueProvider.fetchLeagues(List.of(request.tournamentId())),
                result -> result == null ? 0 : result.size(),
                stages
        );

        if (!leagueOutcome.succeeded()) {
            skip(stages, "TEAMS", "Tournament lookup failed");
            skip(stages, "FIXTURES", "Tournament lookup failed");
            if (request.matchId() != null) skip(stages, "LINEUPS", "Tournament lookup failed");
            return result(stages);
        }

        ProviderLeague league = (leagueOutcome.value() == null ? List.<ProviderLeague>of() : leagueOutcome.value()).stream()
                .filter(Objects::nonNull)
                .filter(item -> String.valueOf(request.tournamentId()).equals(item.externalId()))
                .findFirst()
                .orElse(null);
        if (league == null) {
            replaceStage(stages, "TOURNAMENT_AND_SEASONS",
                    "SofaScore did not return the requested tournament ID");
            skip(stages, "TEAMS", "Requested tournament was not found");
            skip(stages, "FIXTURES", "Requested tournament was not found");
            if (request.matchId() != null) skip(stages, "LINEUPS", "Requested tournament was not found");
            return result(stages);
        }

        ProviderSeason season = league.seasons() == null ? null : league.seasons().stream()
                .filter(Objects::nonNull)
                .filter(item -> matchesSeason(item, request.season()))
                .findFirst()
                .orElse(null);
        if (season == null) {
            replaceStage(stages, "TOURNAMENT_AND_SEASONS",
                    "Tournament was returned but requested season was not found: " + request.season());
            skip(stages, "TEAMS", "Requested season was not found");
            skip(stages, "FIXTURES", "Requested season was not found");
            if (request.matchId() != null) skip(stages, "LINEUPS", "Requested season was not found");
            return result(stages);
        }

        execute(
                "TEAMS",
                () -> teamProvider.fetchTeams(new TeamQuery(
                        request.tournamentId(), season.year(), league.identify())),
                value -> value == null ? 0 : value.size(),
                stages
        );

        execute(
                "FIXTURES",
                () -> fixtureProvider.fetchFixtures(new FixtureQuery(
                        request.tournamentId(), season.year(), request.from(), request.to(), league.identify())),
                value -> value == null ? 0 : value.size(),
                stages
        );

        if (request.matchId() != null) {
            execute(
                    "LINEUPS",
                    () -> lineupsProvider.fetchLineups(String.valueOf(request.matchId()))
                            .orElse(null),
                    this::countLineupPlayers,
                    stages
            );
        }

        return result(stages);
    }

    private void validate(SofaScoreDiagnosticRequest request) {
        if (request == null) throw new IllegalArgumentException("Request body is required");
        if (request.tournamentId() == null || request.tournamentId() <= 0) {
            throw new IllegalArgumentException("tournamentId must be positive");
        }
        if (request.season() == null || request.season().isBlank()) {
            throw new IllegalArgumentException("season must not be blank");
        }
        if (request.from() == null || request.to() == null || request.to().isBefore(request.from())) {
            throw new IllegalArgumentException("from and to are required and to must not be before from");
        }
        if (Duration.between(request.from().atStartOfDay(), request.to().plusDays(1).atStartOfDay())
                .toDays() > MAX_DATE_RANGE_DAYS) {
            throw new IllegalArgumentException("Fixture diagnostic date range must not exceed 31 days");
        }
        if (request.matchId() != null && request.matchId() <= 0) {
            throw new IllegalArgumentException("matchId must be positive when provided");
        }
    }

    private boolean matchesSeason(ProviderSeason season, String requested) {
        return requested.trim().equalsIgnoreCase(season.year())
                || requested.trim().equalsIgnoreCase(season.externalId());
    }

    private <T> Outcome<T> execute(
            String name,
            Supplier<T> operation,
            ToIntFunction<T> itemCounter,
            List<SofaScoreDiagnosticResult.StageResult> stages) {
        long startedNanos = System.nanoTime();
        try {
            T value = operation.get();
            int count = value == null ? 0 : itemCounter.applyAsInt(value);
            long duration = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
            String status = count > 0 ? "SUCCESS" : "EMPTY";
            stages.add(new SofaScoreDiagnosticResult.StageResult(
                    name, status, duration, count,
                    count > 0 ? "SofaScore returned data" : "SofaScore request completed but returned no data"));
            return new Outcome<>(value, true);
        } catch (RuntimeException exception) {
            long duration = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
            stages.add(new SofaScoreDiagnosticResult.StageResult(
                    name, "FAILED", duration, null, safeMessage(exception)));
            return new Outcome<>(null, false);
        }
    }

    private int countLineupPlayers(ProviderLineups lineups) {
        return lineups == null ? 0 : lineups.home().size() + lineups.away().size();
    }

    private void skip(List<SofaScoreDiagnosticResult.StageResult> stages, String name, String reason) {
        stages.add(new SofaScoreDiagnosticResult.StageResult(name, "SKIPPED", 0, null, reason));
    }

    private void replaceStage(List<SofaScoreDiagnosticResult.StageResult> stages, String name, String reason) {
        for (int index = stages.size() - 1; index >= 0; index--) {
            SofaScoreDiagnosticResult.StageResult current = stages.get(index);
            if (current.name().equals(name)) {
                stages.set(index, new SofaScoreDiagnosticResult.StageResult(
                        name, "FAILED", current.durationMs(), current.itemCount(), reason));
                return;
            }
        }
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return exception.getClass().getSimpleName();
        return message.length() <= 300 ? message : message.substring(0, 300);
    }

    private SofaScoreDiagnosticResult result(List<SofaScoreDiagnosticResult.StageResult> stages) {
        boolean failed = stages.stream().anyMatch(stage -> "FAILED".equals(stage.status()));
        boolean empty = stages.stream().anyMatch(stage -> "EMPTY".equals(stage.status()));
        return new SofaScoreDiagnosticResult(
                TARGET_PROVIDER.name(),
                failed ? "FAILED" : empty ? "PARTIAL" : "SUCCESS",
                Instant.now(),
                stages
        );
    }

    private <T> T findProvider(List<T> providers, Predicate<T> providerMatcher) {
        return providers.stream()
                .filter(providerMatcher)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("SofaScore provider is not configured"));
    }

    private record Outcome<T>(T value, boolean succeeded) {
    }
}
