package com.mmo.module.fb.ingest.provider.apifootball;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mmo.module.fb.domain.entity.Bookmaker;
import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.entity.MatchOdds;
import com.mmo.module.fb.domain.entity.MatchExternalId;
import com.mmo.module.fb.domain.entity.OddsHistory;
import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.domain.enums.MatchStatus;
import com.mmo.module.fb.domain.repository.BookmakerRepository;
import com.mmo.module.fb.domain.repository.MatchExternalIdRepository;
import com.mmo.module.fb.domain.repository.MatchOddsRepository;
import com.mmo.module.fb.domain.repository.MatchRepository;
import com.mmo.module.fb.domain.repository.OddsHistoryRepository;
import com.mmo.module.fb.ingest.provider.apifootball.dto.ApiFootballDtos.OddsBetDto;
import com.mmo.module.fb.ingest.provider.apifootball.dto.ApiFootballDtos.OddsBookmakerDto;
import com.mmo.module.fb.ingest.provider.apifootball.dto.ApiFootballDtos.OddsItem;
import com.mmo.module.fb.ingest.provider.apifootball.dto.ApiFootballDtos.OddsValueDto;
import com.mmo.module.fb.ingest.provider.apifootball.dto.ApiFootballResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiFootballOddsSyncService {

    private static final String MARKET = "PREMATCH";
    private static final int MAX_FIXTURES_PER_RUN = 10;
    private static final TypeReference<ApiFootballResponse<OddsItem>> RESPONSE_TYPE = new TypeReference<>() {};

    private final ApiFootballClient client;
    private final MatchRepository matchRepository;
    private final MatchExternalIdRepository matchExternalIdRepository;
    private final BookmakerRepository bookmakerRepository;
    private final MatchOddsRepository matchOddsRepository;
    private final OddsHistoryRepository oddsHistoryRepository;

    @Transactional
    public void syncUpcomingOdds() {
        Instant now = Instant.now();
        Instant until = now.plus(72, ChronoUnit.HOURS);
        List<Match> upcoming = matchRepository.findAll().stream()
                .filter(match -> match.getStatus() == MatchStatus.SCHEDULED)
                .filter(match -> match.getMatchTime() != null
                        && !match.getMatchTime().isBefore(now) && !match.getMatchTime().isAfter(until))
                .filter(match -> matchExternalIdRepository
                        .findByMatchIdAndProvider(match.getId(), Provider.API_FOOTBALL).isPresent())
                .sorted(Comparator.comparing(Match::getMatchTime))
                .limit(MAX_FIXTURES_PER_RUN)
                .toList();

        int processed = 0;
        for (Match match : upcoming) {
            try {
                syncFixtureOdds(match);
                processed++;
            } catch (Exception ex) {
                log.warn("Could not sync odds for match {}: {}", match.getId(), ex.getMessage());
            }
        }
        log.info("API-Football odds sync finished. {} upcoming fixtures checked.", processed);
    }

    private void syncFixtureOdds(Match match) {
        String fixtureId = matchExternalIdRepository
                .findByMatchIdAndProvider(match.getId(), Provider.API_FOOTBALL)
                .map(MatchExternalId::getExternalId)
                .orElseThrow(() -> new IllegalStateException("No API-Football fixture ID"));

        ApiFootballResponse<OddsItem> response = client.get("/odds", Map.of("fixture", fixtureId), RESPONSE_TYPE);
        for (OddsItem item : safe(response.response())) {
            for (OddsBookmakerDto bookmakerDto : safe(item.bookmakers())) {
                if (bookmakerDto.name() == null || bookmakerDto.name().isBlank()) continue;
                Bookmaker bookmaker = bookmakerRepository.findByName(bookmakerDto.name())
                        .orElseGet(() -> bookmakerRepository.save(
                                Bookmaker.builder().name(bookmakerDto.name()).isActive(true).build()));

                MatchOdds odds = matchOddsRepository
                        .findByMatchIdAndBookmakerIdAndMarketName(match.getId(), bookmaker.getId(), MARKET)
                        .orElseGet(() -> MatchOdds.builder()
                                .match(match).bookmaker(bookmaker).marketName(MARKET).build());

                for (OddsBetDto bet : safe(bookmakerDto.bets())) {
                    for (OddsValueDto value : safe(bet.values())) {
                        BigDecimal odd = decimal(value.odd());
                        if (odd == null || bet.name() == null || value.value() == null) continue;
                        applyOdd(odds, bet.name(), value.value(), odd);
                    }
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
    }

    private static void applyOdd(MatchOdds odds, String market, String label, BigDecimal odd) {
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

    private static BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) return null;
        try { return new BigDecimal(value); }
        catch (NumberFormatException ignored) { return null; }
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
