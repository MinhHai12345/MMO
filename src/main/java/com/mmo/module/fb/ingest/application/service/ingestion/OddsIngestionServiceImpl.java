package com.mmo.module.fb.ingest.application.service.ingestion;

import com.mmo.module.fb.domain.match.enums.MatchEnrichmentStatus;
import com.mmo.module.fb.ingest.application.service.support.IngestionServiceSupport;

import com.mmo.AppProperties;
import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.match.entity.MatchExternalId;
import com.mmo.module.fb.domain.match.enums.MatchStatus;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.domain.match.repository.MatchExternalIdRepository;
import com.mmo.module.fb.domain.match.repository.MatchRepository;
import com.mmo.module.fb.domain.match.enums.MatchEnrichmentResource;
import com.mmo.module.fb.domain.match.enums.MatchProvider;
import com.mmo.module.fb.domain.match.service.MatchEnrichmentStateService;
import com.mmo.module.fb.ingest.provider.model.OddsQuery;
import com.mmo.module.fb.ingest.provider.model.ProviderOdds;
import com.mmo.module.fb.ingest.application.registry.ProviderIngestionRegistry;
import com.mmo.module.fb.ingest.application.port.in.OddsIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import com.mmo.module.fb.ingest.application.port.in.FixtureIngestionService;
import com.mmo.module.fb.ingest.application.port.out.OddsPersistencePort;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OddsIngestionServiceImpl implements OddsIngestionService {

    private static final int MAX_FIXTURES_PER_RUN = 10;

    private final ProviderIngestionRegistry providerRegistry;
    private final AppProperties appProperties;
    private final IngestionServiceSupport ingestionSupport;
    private final FixtureIngestionService fixtureIngestionService;
    private final MatchRepository matchRepository;
    private final MatchExternalIdRepository matchExternalIdRepository;
    private final OddsPersistencePort oddsPersistenceService;
    private final MatchEnrichmentStateService enrichmentStateService;

    /**
     * Coordinates provider I/O and database work without holding a transaction open
     * while waiting for the upstream odds API.
     */
    @Override
    public void syncUpcomingOdds() {
        syncUpcomingOdds(appProperties.getIngestion().getProviders().getOdds());
    }

    @Override
    public void syncUpcomingOdds(Provider selectedProvider) {
        ingestionSupport.requireProviderEnabled(selectedProvider, "odds");

        Instant now = Instant.now();
        Instant until = now.plus(72, ChronoUnit.HOURS);
        List<Match> scheduledMatches = matchRepository.findByStatusAndMatchTimeBetweenOrderByMatchTimeAsc(
                MatchStatus.SCHEDULED, now, until);
        Map<Long, String> fixtureIdsByMatchId = loadFixtureIds(selectedProvider);

        final Map<Long, String> checkFixtureIds = fixtureIdsByMatchId;
        boolean missingProviderMappings = !scheduledMatches.isEmpty()
                && scheduledMatches.stream().anyMatch(match -> !checkFixtureIds.containsKey(match.getId()));
        if (missingProviderMappings) {
            LocalDate today = LocalDate.now();
            fixtureIngestionService.syncFixturesForProvider(selectedProvider, today, today.plusDays(3));
            fixtureIdsByMatchId = loadFixtureIds(selectedProvider);
        }

        List<Match> upcoming = matchRepository.findUpcomingWithProviderMapping(
                MatchStatus.SCHEDULED, now, until, MatchProvider.fromName(selectedProvider.name()), MatchEnrichmentResource.ODDS,
                MatchEnrichmentStatus.SUCCEEDED,
                MatchEnrichmentStatus.IN_PROGRESS,
                now.minus(30, ChronoUnit.MINUTES), PageRequest.of(0, MAX_FIXTURES_PER_RUN));

        int processed = 0;
        for (Match match : upcoming) {
            String fixtureId = fixtureIdsByMatchId.get(match.getId());
            if (fixtureId == null || fixtureId.isBlank()) {
                log.warn("Skipping odds for match {} because provider {} mapping is missing",
                        match.getId(), selectedProvider);
                continue;
            }

            enrichmentStateService.start(match, MatchProvider.fromName(selectedProvider.name()), MatchEnrichmentResource.ODDS);
            try {
                // Network call deliberately happens before entering the persistence transaction.
                List<ProviderOdds> values = providerRegistry.odds(selectedProvider)
                        .fetchOdds(new OddsQuery(fixtureId));
                oddsPersistenceService.persistOdds(match.getId(), selectedProvider, values);
                if (values == null || values.isEmpty()) {
                    enrichmentStateService.empty(match, MatchProvider.fromName(selectedProvider.name()), MatchEnrichmentResource.ODDS);
                } else {
                    enrichmentStateService.succeed(match, MatchProvider.fromName(selectedProvider.name()), MatchEnrichmentResource.ODDS);
                }
                processed++;
            } catch (Exception ex) {
                enrichmentStateService.failed(match, MatchProvider.fromName(selectedProvider.name()), MatchEnrichmentResource.ODDS, ex);
                log.warn("Could not sync odds for match {}: {}", match.getId(), ex.getMessage());
            }
        }
        log.info("Odds ingestion finished. {} upcoming fixtures checked.", processed);
    }

    private Map<Long, String> loadFixtureIds(Provider provider) {
        List<MatchExternalId> mappings = matchExternalIdRepository.findAllByProvider(MatchProvider.fromName(provider.name()));
        Map<Long, String> fixtureIdsByMatchId = new HashMap<>();
        mappings.forEach(mapping -> {
            Long matchId = mapping.getMatch().getId();
            fixtureIdsByMatchId.put(matchId, mapping.getExternalId());
        });
        return fixtureIdsByMatchId;
    }
}
