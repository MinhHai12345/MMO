package com.mmo.module.fb.ingest.service;

import com.mmo.AppProperties;
import com.mmo.module.fb.domain.entity.Bookmaker;
import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.entity.MatchExternalId;
import com.mmo.module.fb.domain.entity.MatchOdds;
import com.mmo.module.fb.domain.entity.OddsHistory;
import com.mmo.module.fb.domain.enums.MatchStatus;
import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.domain.repository.BookmakerRepository;
import com.mmo.module.fb.domain.repository.MatchExternalIdRepository;
import com.mmo.module.fb.domain.repository.MatchOddsRepository;
import com.mmo.module.fb.domain.repository.MatchRepository;
import com.mmo.module.fb.domain.repository.OddsHistoryRepository;
import com.mmo.module.fb.ingest.provider.model.OddsQuery;
import com.mmo.module.fb.ingest.provider.model.ProviderOdds;
import com.mmo.module.fb.ingest.provider.spi.ProviderRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OddsIngestionService {

    private static final String MARKET = "PREMATCH";
    private static final int MAX_FIXTURES_PER_RUN = 10;

    private final ProviderRegistry providerRegistry;
    private final AppProperties appProperties;
    private final FixtureIngestionService fixtureIngestionService;
    private final MatchRepository matchRepository;
    private final MatchExternalIdRepository matchExternalIdRepository;
    private final BookmakerRepository bookmakerRepository;
    private final MatchOddsRepository matchOddsRepository;
    private final OddsHistoryRepository oddsHistoryRepository;

    @Transactional
    public void syncUpcomingOdds() {
        Provider selectedProvider = appProperties.getIngestion().getProviders().getOdds();
        if (selectedProvider == Provider.API_FOOTBALL && (appProperties.getApiFootball() == null || !appProperties.getApiFootball().isEnabled())) return;

        // Ensure this provider's fixture IDs are linked to canonical matches before odds lookup.
        LocalDate today = LocalDate.now();
        fixtureIngestionService.syncFixturesForProvider(selectedProvider, today, today.plusDays(3));

        Instant now = Instant.now();
        Instant until = now.plus(72, ChronoUnit.HOURS);
        List<Match> upcoming = matchRepository.findAll().stream()
                .filter(match -> match.getStatus() == MatchStatus.SCHEDULED)
                .filter(match -> match.getMatchTime() != null
                        && !match.getMatchTime().isBefore(now) && !match.getMatchTime().isAfter(until))
                .filter(match -> matchExternalIdRepository
                        .findByMatchIdAndProvider(match.getId(), selectedProvider).isPresent())
                .sorted(Comparator.comparing(Match::getMatchTime))
                .limit(MAX_FIXTURES_PER_RUN)
                .toList();

        int processed = 0;
        for (Match match : upcoming) {
            try {
                syncFixtureOdds(match, selectedProvider);
                processed++;
            } catch (Exception ex) {
                log.warn("Could not sync odds for match {}: {}", match.getId(), ex.getMessage());
            }
        }
        log.info("Odds ingestion finished. {} upcoming fixtures checked.", processed);
    }

    private void syncFixtureOdds(Match match, Provider selectedProvider) {
        String fixtureId = matchExternalIdRepository
                .findByMatchIdAndProvider(match.getId(), selectedProvider)
                .map(MatchExternalId::getExternalId)
                .orElseThrow(() -> new IllegalStateException("No fixture ID for provider " + selectedProvider));

        List<ProviderOdds> values = providerRegistry.odds(selectedProvider).fetchOdds(new OddsQuery(fixtureId));
        Map<String, List<ProviderOdds>> byBookmaker = new LinkedHashMap<>();
        for (ProviderOdds value : values) {
            if (value.bookmaker() != null && !value.bookmaker().isBlank()) {
                byBookmaker.computeIfAbsent(value.bookmaker(), ignored -> new java.util.ArrayList<>()).add(value);
            }
        }

        for (Map.Entry<String, List<ProviderOdds>> entry : byBookmaker.entrySet()) {
            Bookmaker bookmaker = bookmakerRepository.findByName(entry.getKey())
                    .orElseGet(() -> bookmakerRepository.save(
                            Bookmaker.builder().name(entry.getKey()).isActive(true).build()));

            MatchOdds odds = matchOddsRepository
                    .findByMatchIdAndBookmakerIdAndMarketName(match.getId(), bookmaker.getId(), MARKET)
                    .orElseGet(() -> MatchOdds.builder()
                            .match(match).bookmaker(bookmaker).marketName(MARKET).build());

            for (ProviderOdds value : entry.getValue()) {
                applyOdd(odds, value.market(), value.outcome(), value.odd());
            }

            odds = matchOddsRepository.save(odds);
            if (odds.getHomeWinOdds() != null || odds.getDrawOdds() != null || odds.getAwayWinOdds() != null) {
                oddsHistoryRepository.save(OddsHistory.builder()
                        .matchOdds(odds)
                        .homeWinOdds(odds.getHomeWinOdds())
                        .drawOdds(odds.getDrawOdds())
                        .awayWinOdds(odds.getAwayWinOdds())
                        .recordedAt(Instant.now())
                        .build());
            }
        }
    }



    private static void applyOdd(MatchOdds odds, String market, String label, java.math.BigDecimal odd) {
        if (market == null || label == null || odd == null) return;
        if (market.equalsIgnoreCase("Match Winner")) {
            if (label.equalsIgnoreCase("Home")) odds.setHomeWinOdds(odd);
            else if (label.equalsIgnoreCase("Draw")) odds.setDrawOdds(odd);
            else if (label.equalsIgnoreCase("Away")) odds.setAwayWinOdds(odd);
        } else if (market.equalsIgnoreCase("Goals Over/Under")) {
            if (label.equalsIgnoreCase("Over 2.5")) odds.setOver25Odds(odd);
            else if (label.equalsIgnoreCase("Under 2.5")) odds.setUnder25Odds(odd);
        } else if (market.equalsIgnoreCase("Both Teams Score")) {
            if (label.equalsIgnoreCase("Yes")) odds.setBttsYesOdds(odd);
            else if (label.equalsIgnoreCase("No")) odds.setBttsNoOdds(odd);
        }
    }
}
