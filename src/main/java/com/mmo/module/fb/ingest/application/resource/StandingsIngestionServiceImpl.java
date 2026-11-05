package com.mmo.module.fb.ingest.application.resource;

import com.mmo.module.fb.ingest.application.exception.IngestionNotAvailableException;
import com.mmo.module.fb.ingest.application.exception.IngestionPartialException;
import com.mmo.module.fb.ingest.application.support.IngestionServiceSupport;
import com.mmo.AppProperties;
import com.mmo.module.fb.domain.league.entity.Season;
import com.mmo.module.fb.ingest.provider.footballdata.client.FootballDataRateLimitException;
import com.mmo.module.fb.ingest.provider.footballdata.client.FootballDataRestrictedResourceException;
import com.mmo.module.fb.domain.team.entity.TeamExternalId;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.domain.league.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.domain.team.repository.TeamExternalIdRepository;
import com.mmo.module.fb.ingest.provider.model.ProviderStanding;
import com.mmo.module.fb.ingest.provider.model.StandingsQuery;
import com.mmo.module.fb.ingest.provider.spi.ProviderRegistry;
import com.mmo.module.fb.ingest.provider.spi.StandingsProvider;
import com.mmo.module.fb.ingest.service.StandingsIngestionService;
import com.mmo.module.fb.ingest.service.StandingsPersistenceService;
import com.mmo.module.fb.ingest.service.TeamIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class StandingsIngestionServiceImpl implements StandingsIngestionService {

    private final ProviderRegistry providerRegistry;
    private final AppProperties appProperties;
    private final IngestionServiceSupport ingestionSupport;
    private final TeamIngestionService teamIngestionService;
    private final LeagueExternalIdRepository leagueExternalIdRepository;
    private final TeamExternalIdRepository teamExternalIdRepository;
    private final StandingsPersistenceService standingsPersistenceService;

    @Override
    public void syncStandings() {
        syncStandingsForProvider(appProperties.getIngestion().getProviders().getStandings());
    }

    @Override
    public void syncStandingsForProvider(Provider selectedProvider) {
        syncStandingsForProvider(selectedProvider, null);
    }

    @Override
    public void syncSeasonForProvider(Provider selectedProvider, Long seasonId) {
        if (seasonId == null) throw new IllegalArgumentException("seasonId must not be null");
        syncStandingsForProvider(selectedProvider, seasonId);
    }

    /**
     * Provider calls happen outside a transaction; each standing row is persisted in its own
     * short transaction. All mapped seasons are audited, including historical seasons.
     */
    private void syncStandingsForProvider(Provider selectedProvider, Long onlySeasonId) {
        ingestionSupport.requireProviderEnabled(selectedProvider, "standings");

        if (teamExternalIdRepository.findAllByProvider(selectedProvider).isEmpty()) {
            if (onlySeasonId != null) {
                teamIngestionService.syncSeasonForProvider(selectedProvider, onlySeasonId);
            } else {
                teamIngestionService.syncTeamsForProvider(selectedProvider);
            }
        }

        StandingsProvider provider = providerRegistry.standings(selectedProvider);
        Map<Long, String> leagueExternalIdsByLeagueId = new HashMap<>();
        leagueExternalIdRepository.findAllByProvider(selectedProvider).forEach(mapping ->
                leagueExternalIdsByLeagueId.put(mapping.getLeague().getId(), mapping.getExternalId()));

        Map<String, Long> teamIdsByExternalId = new HashMap<>();
        for (TeamExternalId mapping : teamExternalIdRepository.findAllByProvider(selectedProvider)) {
            teamIdsByExternalId.put(mapping.getExternalId(), mapping.getTeam().getId());
        }

        List<Season> seasons = ingestionSupport.standingsSeasonsToSync(selectedProvider);
        if (onlySeasonId != null) {
            seasons = seasons.stream().filter(season -> onlySeasonId.equals(season.getId())).toList();
            if (seasons.isEmpty()) {
                throw new IllegalArgumentException("No mapped season owned by " + selectedProvider
                        + " found for seasonId=" + onlySeasonId);
            }
        }

        int fetchedRows = 0;
        int persistedRows = 0;
        int unavailableSeasons = 0;
        int unmappedRows = 0;
        int invalidRows = 0;
        int duplicateRows = 0;
        boolean rateLimitInterrupted = false;
        boolean partial = false;

        log.info("Fetching standings for {} mapped league-season records via {}",
                seasons.size(), selectedProvider);

        for (Season season : seasons) {
            if (season.getLeague() == null || season.getYear() == null) continue;

            Long externalLeagueId = IngestionServiceSupport.parseExternalId(
                    leagueExternalIdsByLeagueId.get(season.getLeague().getId()));
            if (externalLeagueId == null) {
                unavailableSeasons++;
                log.warn("Standings NOT_AVAILABLE: provider={} league={} season={} reason=missing-league-mapping",
                        selectedProvider, season.getLeague().getId(), season.getYear());
                continue;
            }

            List<ProviderStanding> rows;
            try {
                rows = provider.fetchStandings(new StandingsQuery(externalLeagueId, season.getYear()));
            } catch (FootballDataRestrictedResourceException ex) {
                if (selectedProvider != Provider.FOOTBALL_DATA) throw ex;
                unavailableSeasons++;
                log.warn("Standings NOT_AVAILABLE: provider={} league={} season={} reason=restricted-resource detail={}",
                        selectedProvider, externalLeagueId, season.getYear(), ex.getMessage());
                continue;
            } catch (FootballDataRateLimitException ex) {
                if (selectedProvider != Provider.FOOTBALL_DATA) throw ex;
                rateLimitInterrupted = true;
                log.warn("Stopping standings ingestion via {} after repeated rate limiting. "
                                + "Persisted {} rows; remaining seasons will be retried. {}",
                        selectedProvider, persistedRows, ex.getMessage());
                break;
            }

            if (rows == null || rows.isEmpty()) {
                unavailableSeasons++;
                String reason = season.getStartDate() != null && season.getStartDate().isAfter(LocalDate.now())
                        ? "season-not-started" : "provider-returned-empty-standings";
                log.info("Standings NOT_AVAILABLE: provider={} league={} season={} reason={}; existing rows preserved",
                        selectedProvider, externalLeagueId, season.getYear(), reason);
                continue;
            }

            fetchedRows += rows.size();
            Set<String> seenRows = new HashSet<>();
            int seasonPersisted = 0;
            int seasonUnmapped = 0;
            int seasonInvalid = 0;
            int seasonDuplicates = 0;

            for (ProviderStanding row : rows) {
                if (row == null) {
                    seasonInvalid++;
                    continue;
                }
                List<String> issues = row.consistencyIssues();
                if (!issues.isEmpty()) {
                    seasonInvalid++;
                    log.warn("Rejecting inconsistent standings row: provider={} league={} season={} team={} type={} group={} issues={}",
                            selectedProvider, externalLeagueId, season.getYear(), row.teamExternalId(),
                            row.standingType(), row.groupName(), issues);
                    continue;
                }

                String groupKey = row.groupName() == null ? ""
                        : row.groupName().trim().toUpperCase(Locale.ROOT);
                String rowKey = row.teamExternalId() + "|" + row.standingType() + "|" + groupKey;
                if (!seenRows.add(rowKey)) {
                    seasonDuplicates++;
                    log.warn("Duplicate standings row in provider response: provider={} league={} season={} key={}",
                            selectedProvider, externalLeagueId, season.getYear(), rowKey);
                    continue;
                }

                Long teamId = teamIdsByExternalId.get(row.teamExternalId());
                if (teamId == null) {
                    seasonUnmapped++;
                    log.warn("Standings row has no canonical team mapping: provider={} league={} season={} teamExternalId={}",
                            selectedProvider, externalLeagueId, season.getYear(), row.teamExternalId());
                    continue;
                }

                standingsPersistenceService.upsertStanding(selectedProvider, season.getId(), teamId, row);
                seasonPersisted++;
            }

            persistedRows += seasonPersisted;
            unmappedRows += seasonUnmapped;
            invalidRows += seasonInvalid;
            duplicateRows += seasonDuplicates;
            if (seasonUnmapped > 0 || seasonInvalid > 0 || seasonDuplicates > 0) partial = true;

            log.info("Standings coverage: provider={} league={} season={} fetched={} persisted={} unmappedTeams={} invalid={} duplicates={} status={}",
                    selectedProvider, externalLeagueId, season.getYear(), rows.size(), seasonPersisted,
                    seasonUnmapped, seasonInvalid, seasonDuplicates,
                    seasonPersisted == 0 ? "NO_CANONICAL_ROWS" :
                            (seasonUnmapped + seasonInvalid + seasonDuplicates > 0 ? "PARTIAL" : "COMPLETE"));
        }

        log.info("Standings sync report: provider={} seasons={} unavailableSeasons={} fetchedRows={} persistedRows={} unmappedRows={} invalidRows={} duplicateRows={} rateLimited={}",
                selectedProvider, seasons.size(), unavailableSeasons, fetchedRows, persistedRows,
                unmappedRows, invalidRows, duplicateRows, rateLimitInterrupted);

        if (rateLimitInterrupted) {
            throw new IngestionPartialException("Standings ingestion interrupted by provider rate limiting");
        }
        if (onlySeasonId != null && unavailableSeasons > 0 && fetchedRows == 0) {
            throw new IngestionNotAvailableException("Provider " + selectedProvider
                    + " does not currently expose standings for seasonId=" + onlySeasonId);
        }
        if (onlySeasonId != null && persistedRows == 0 && fetchedRows > 0) {
            throw new IngestionPartialException("Standings response contained no persistable canonical team rows");
        }
        if (partial) {
            throw new IngestionPartialException("Standings sync contains unmapped, invalid or duplicate rows");
        }
    }
}
