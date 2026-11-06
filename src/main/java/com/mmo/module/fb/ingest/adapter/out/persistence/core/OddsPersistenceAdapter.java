package com.mmo.module.fb.ingest.adapter.out.persistence.core;

import com.mmo.module.fb.domain.match.entity.Bookmaker;
import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.match.entity.MatchOdds;
import com.mmo.module.fb.domain.match.entity.OddsHistory;
import com.mmo.module.fb.domain.match.repository.BookmakerRepository;
import com.mmo.module.fb.domain.match.repository.MatchOddsRepository;
import com.mmo.module.fb.domain.match.repository.MatchRepository;
import com.mmo.module.fb.domain.match.repository.OddsHistoryRepository;
import com.mmo.module.fb.domain.match.service.MatchOddsQuotePersistenceService;
import com.mmo.module.fb.domain.match.enums.MatchProvider;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.application.model.provider.ProviderOdds;
import com.mmo.module.fb.ingest.application.port.out.OddsPersistencePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class OddsPersistenceAdapter implements OddsPersistencePort {

    private static final String MARKET = "PREMATCH";

    private final MatchRepository matchRepository;
    private final BookmakerRepository bookmakerRepository;
    private final MatchOddsRepository matchOddsRepository;
    private final OddsHistoryRepository oddsHistoryRepository;
    private final MatchOddsQuotePersistenceService quotePersistenceService;

    @Override
    @Transactional
    public void persistOdds(Long matchId, List<ProviderOdds> values) {
        persistOdds(matchId, Provider.API_FOOTBALL, values);
    }

    @Override
    @Transactional
    public void persistOdds(Long matchId, Provider provider, List<ProviderOdds> values) {
        if (matchId == null || provider == null || values == null || values.isEmpty()) return;
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found: " + matchId));

        Map<String, List<ProviderOdds>> byBookmakerName = values.stream()
                .filter(Objects::nonNull)
                .filter(value -> value.bookmaker() != null && !value.bookmaker().isBlank())
                .collect(Collectors.groupingBy(ProviderOdds::bookmaker, LinkedHashMap::new, Collectors.toList()));
        if (byBookmakerName.isEmpty()) return;

        Map<String, Bookmaker> bookmakersByName = bookmakerRepository
                .findAllByNameIn(byBookmakerName.keySet()).stream()
                .collect(Collectors.toMap(Bookmaker::getName, bookmaker -> bookmaker, (left, right) -> left));
        List<Bookmaker> missingBookmakers = byBookmakerName.keySet().stream()
                .filter(name -> !bookmakersByName.containsKey(name))
                .map(name -> Bookmaker.builder().name(name).isActive(true).build()).toList();
        if (!missingBookmakers.isEmpty()) {
            bookmakerRepository.saveAll(missingBookmakers)
                    .forEach(bookmaker -> bookmakersByName.put(bookmaker.getName(), bookmaker));
        }

        Map<Bookmaker, List<ProviderOdds>> valuesByBookmaker = new LinkedHashMap<>();
        byBookmakerName.forEach((name, bookmakerValues) -> {
            Bookmaker bookmaker = bookmakersByName.get(name);
            if (bookmaker != null) valuesByBookmaker.put(bookmaker, bookmakerValues);
        });

        Map<Long, MatchOdds> existingByBookmaker = matchOddsRepository.findAllByMatchId(matchId).stream()
                .filter(odds -> MARKET.equals(odds.getMarketName()))
                .collect(Collectors.toMap(odds -> odds.getBookmaker().getId(), odds -> odds, (left, right) -> left));
        Map<Long, MatchOdds> pending = new LinkedHashMap<>();
        Map<Long, boolean[]> resultOddsChanged = new LinkedHashMap<>();
        for (Map.Entry<Bookmaker, List<ProviderOdds>> entry : valuesByBookmaker.entrySet()) {
            Bookmaker bookmaker = entry.getKey();
            MatchOdds odds = existingByBookmaker.get(bookmaker.getId());
            if (odds == null) {
                odds = MatchOdds.builder().match(match).bookmaker(bookmaker).marketName(MARKET).build();
            }
            BigDecimal previousHome = odds.getHomeWinOdds();
            BigDecimal previousDraw = odds.getDrawOdds();
            BigDecimal previousAway = odds.getAwayWinOdds();
            for (ProviderOdds value : entry.getValue()) {
                applyOdd(odds, value.market(), value.outcome(), value.odd());
            }
            boolean changed = !sameOdd(previousHome, odds.getHomeWinOdds())
                    || !sameOdd(previousDraw, odds.getDrawOdds())
                    || !sameOdd(previousAway, odds.getAwayWinOdds());
            pending.put(bookmaker.getId(), odds);
            resultOddsChanged.put(bookmaker.getId(), new boolean[]{changed});
        }

        if (!pending.isEmpty()) {
            List<MatchOdds> savedOdds = matchOddsRepository.saveAll(pending.values());
            List<OddsHistory> histories = new ArrayList<>();
            for (MatchOdds odds : savedOdds) {
                boolean changed = resultOddsChanged.getOrDefault(
                        odds.getBookmaker().getId(), new boolean[]{false})[0];
                if (changed && (odds.getHomeWinOdds() != null || odds.getDrawOdds() != null
                        || odds.getAwayWinOdds() != null)) {
                    histories.add(OddsHistory.builder().matchOdds(odds)
                            .homeWinOdds(odds.getHomeWinOdds()).drawOdds(odds.getDrawOdds())
                            .awayWinOdds(odds.getAwayWinOdds()).recordedAt(Instant.now()).build());
                }
            }
            if (!histories.isEmpty()) oddsHistoryRepository.saveAll(histories);
        }

        // Quote identities are fetched once per match/provider and flushed as two batches.
        Map<Bookmaker, List<MatchOddsQuotePersistenceService.QuoteInput>> quoteInputs = new LinkedHashMap<>();
        valuesByBookmaker.forEach((bookmaker, bookmakerValues) -> quoteInputs.put(bookmaker,
                bookmakerValues.stream().map(value ->
                        new MatchOddsQuotePersistenceService.QuoteInput(
                                value.market(), value.outcome(), value.odd())).toList()));
        quotePersistenceService.upsertAll(match, MatchProvider.fromName(provider.name()), quoteInputs);
    }

    private static boolean sameOdd(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }

    private static void applyOdd(MatchOdds odds, String market, String label, BigDecimal odd) {
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