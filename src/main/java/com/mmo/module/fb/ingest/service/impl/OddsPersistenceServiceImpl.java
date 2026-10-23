package com.mmo.module.fb.ingest.service.impl;

import com.mmo.module.fb.domain.entity.Bookmaker;
import com.mmo.module.fb.match.entity.Match;
import com.mmo.module.fb.match.entity.MatchOdds;
import com.mmo.module.fb.match.entity.OddsHistory;
import com.mmo.module.fb.domain.repository.BookmakerRepository;
import com.mmo.module.fb.match.repository.MatchOddsRepository;
import com.mmo.module.fb.match.repository.MatchRepository;
import com.mmo.module.fb.match.repository.OddsHistoryRepository;
import com.mmo.module.fb.ingest.provider.model.ProviderOdds;
import com.mmo.module.fb.ingest.service.OddsPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OddsPersistenceServiceImpl implements OddsPersistenceService {

    private static final String MARKET = "PREMATCH";

    private final MatchRepository matchRepository;
    private final BookmakerRepository bookmakerRepository;
    private final MatchOddsRepository matchOddsRepository;
    private final OddsHistoryRepository oddsHistoryRepository;

    @Transactional
    public void persistOdds(Long matchId, List<ProviderOdds> values) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found: " + matchId));

        Map<String, List<ProviderOdds>> byBookmaker = new LinkedHashMap<>();
        if (values != null) {
            for (ProviderOdds value : values) {
                if (value != null && value.bookmaker() != null && !value.bookmaker().isBlank()) {
                    byBookmaker.computeIfAbsent(value.bookmaker(), ignored -> new ArrayList<>()).add(value);
                }
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

            BigDecimal previousHome = odds.getHomeWinOdds();
            BigDecimal previousDraw = odds.getDrawOdds();
            BigDecimal previousAway = odds.getAwayWinOdds();

            for (ProviderOdds value : entry.getValue()) {
                applyOdd(odds, value.market(), value.outcome(), value.odd());
            }

            boolean resultOddsChanged = !sameOdd(previousHome, odds.getHomeWinOdds())
                                        || !sameOdd(previousDraw, odds.getDrawOdds())
                                        || !sameOdd(previousAway, odds.getAwayWinOdds());

            odds = matchOddsRepository.save(odds);
            if (resultOddsChanged
                && (odds.getHomeWinOdds() != null || odds.getDrawOdds() != null || odds.getAwayWinOdds() != null)) {
                oddsHistoryRepository.save(OddsHistory.builder()
                        .matchOdds(odds)
                        .homeWinOdds(odds.getHomeWinOdds())
                        .drawOdds(odds.getDrawOdds())
                        .awayWinOdds(odds.getAwayWinOdds())
                        .recordedAt(Instant.now())
                        .build());
            }
        }

        // An empty but successful provider response still counts as a successful sync.
        match.setLastOddsSyncedAt(Instant.now());
        matchRepository.save(match);
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
