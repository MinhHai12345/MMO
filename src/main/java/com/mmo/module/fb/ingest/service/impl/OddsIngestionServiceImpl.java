package com.mmo.module.fb.ingest.service.impl;

import com.mmo.AppProperties;
import com.mmo.module.fb.match.entity.Match;
import com.mmo.module.fb.match.entity.MatchExternalId;
import com.mmo.module.fb.match.enums.MatchStatus;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.match.repository.MatchExternalIdRepository;
import com.mmo.module.fb.match.repository.MatchRepository;
import com.mmo.module.fb.ingest.provider.model.OddsQuery;
import com.mmo.module.fb.ingest.provider.model.ProviderOdds;
import com.mmo.module.fb.ingest.provider.spi.ProviderRegistry;
import com.mmo.module.fb.ingest.service.OddsIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import com.mmo.module.fb.ingest.service.FixtureIngestionService;
import com.mmo.module.fb.ingest.service.OddsPersistenceService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class OddsIngestionServiceImpl implements OddsIngestionService {

    private static final int MAX_FIXTURES_PER_RUN = 10;

    private final ProviderRegistry providerRegistry;
    private final AppProperties appProperties;
    private final FixtureIngestionService fixtureIngestionService;
    private final MatchRepository matchRepository;
    private final MatchExternalIdRepository matchExternalIdRepository;
    private final OddsPersistenceService oddsPersistenceService;

    /**
     * Coordinates provider I/O and database work without holding a transaction open
     * while waiting for the upstream odds API.
     */
    public void syncUpcomingOdds() {
        Provider selectedProvider = appProperties.getIngestion().getProviders().getOdds();
        if (selectedProvider == Provider.API_FOOTBALL
                && (appProperties.getApiFootball() == null || !appProperties.getApiFootball().isEnabled())) {
            return;
        }

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
                MatchStatus.SCHEDULED, now, until, selectedProvider, PageRequest.of(0, MAX_FIXTURES_PER_RUN));

        int processed = 0;
        for (Match match : upcoming) {
            String fixtureId = fixtureIdsByMatchId.get(match.getId());
            if (fixtureId == null || fixtureId.isBlank()) {
                log.warn("Skipping odds for match {} because provider {} mapping is missing",
                        match.getId(), selectedProvider);
                continue;
            }

            try {
                // Network call deliberately happens before entering the persistence transaction.
                List<ProviderOdds> values = providerRegistry.odds(selectedProvider)
                        .fetchOdds(new OddsQuery(fixtureId));
                oddsPersistenceService.persistOdds(match.getId(), values);
                processed++;
            } catch (Exception ex) {
                log.warn("Could not sync odds for match {}: {}", match.getId(), ex.getMessage());
            }
        }
        log.info("Odds ingestion finished. {} upcoming fixtures checked.", processed);
    }

    private Map<Long, String> loadFixtureIds(Provider provider) {
        List<MatchExternalId> mappings = matchExternalIdRepository.findAllByProvider(provider);
        Map<Long, String> fixtureIdsByMatchId = new HashMap<>();
        mappings.forEach(mapping -> {
            Long matchId = mapping.getMatch().getId();
            fixtureIdsByMatchId.put(matchId, mapping.getExternalId());
        });
        return fixtureIdsByMatchId;
    }
}
