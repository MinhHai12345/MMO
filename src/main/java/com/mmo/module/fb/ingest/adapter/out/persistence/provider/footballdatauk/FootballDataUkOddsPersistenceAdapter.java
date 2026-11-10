package com.mmo.module.fb.ingest.adapter.out.persistence.provider.footballdatauk;

import com.mmo.module.fb.domain.match.entity.Bookmaker;
import com.mmo.module.fb.ingest.application.port.out.FootballDataUkOddsPersistencePort;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.DataSourcePolicy;
import com.mmo.module.fb.ingest.policy.Dataset;
import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.match.entity.MatchOdds;
import com.mmo.module.fb.domain.match.repository.BookmakerRepository;
import com.mmo.module.fb.domain.match.repository.MatchOddsRepository;
import com.mmo.module.fb.domain.match.service.MatchOddsQuotePersistenceService;
import com.mmo.module.fb.domain.match.enums.MatchProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.function.Consumer;

/**
 * Maps Football-Data.co.uk bookmaker and market-aggregate prices into MMO Core.
 * Opening and closing prices are separate markets; Max/Avg values are not bookmakers.
 */
@Repository
@RequiredArgsConstructor
public class FootballDataUkOddsPersistenceAdapter implements FootballDataUkOddsPersistencePort {

    private static final List<BookmakerSource> BOOKMAKERS = List.of(
            new BookmakerSource("1XBet", List.of("1XB"), List.of("1XB"), List.of("1XB")),
            new BookmakerSource("Bet365", List.of("B365"), List.of("B365"), List.of("B365")),
            new BookmakerSource("Betfair", List.of("BF"), List.of("BF"), List.of("BF")),
            new BookmakerSource("Betfred", List.of("BFD"), List.of("BFD"), List.of("BFD")),
            new BookmakerSource("BetMGM", List.of("BMGM"), List.of("BMGM"), List.of("BMGM")),
            new BookmakerSource("BetVictor", List.of("BV", "VC"), List.of("BV", "VC"), List.of("BV", "VC")),
            new BookmakerSource("Blue Square", List.of("BS"), List.of("BS"), List.of("BS")),
            new BookmakerSource("Bet&Win", List.of("BW"), List.of("BW"), List.of("BW")),
            new BookmakerSource("Coral", List.of("CL"), List.of("CL"), List.of("CL")),
            new BookmakerSource("Gamebookers", List.of("GB"), List.of("GB"), List.of("GB")),
            new BookmakerSource("Interwetten", List.of("IW"), List.of("IW"), List.of("IW")),
            new BookmakerSource("Ladbrokes", List.of("LB"), List.of("LB"), List.of("LB")),
            new BookmakerSource("Paddy Power", List.of("PP"), List.of("PP"), List.of("PP")),
            new BookmakerSource("Pinnacle", List.of("PS", "P"), List.of("P"), List.of("P")),
            new BookmakerSource("Skybet", List.of("SK"), List.of("SK"), List.of("SK")),
            new BookmakerSource("Sporting Odds", List.of("SO"), List.of("SO"), List.of("SO")),
            new BookmakerSource("Sportingbet", List.of("SB"), List.of("SB"), List.of("SB")),
            new BookmakerSource("Stan James", List.of("SJ"), List.of("SJ"), List.of("SJ")),
            new BookmakerSource("Stanleybet", List.of("SY"), List.of("SY"), List.of("SY")),
            new BookmakerSource("William Hill", List.of("WH"), List.of("WH"), List.of("WH")),
            new BookmakerSource("Betfair Exchange", List.of("BFE"), List.of("BFE"), List.of("BFE"))
    );

    private static final List<AggregateSource> AGGREGATES = List.of(
            new AggregateSource("BetBrain Max", "BBMX", true),
            new AggregateSource("BetBrain Average", "BBAV", true),
            new AggregateSource("Market Max", "MAX", false),
            new AggregateSource("Market Average", "AVG", false)
    );

    private final BookmakerRepository bookmakerRepository;
    private final MatchOddsRepository matchOddsRepository;
    private final MatchOddsQuotePersistenceService quotePersistenceService;
    private final DataSourcePolicy dataSourcePolicy;

    @Override
    @Transactional
    public int persist(Match match, String seasonYear, Map<String, String> row) {
        return persistAll(List.of(new OddsInput(match, seasonYear, row)));
    }

    @Override
    @Transactional
    public int persistAll(List<OddsInput> inputs) {
        if (inputs == null || inputs.isEmpty()) return 0;
        List<OddsInput> valid = inputs.stream()
                .filter(input -> input != null && input.match() != null && input.match().getId() != null
                        && StringUtils.hasText(input.seasonYear()) && input.row() != null
                        && dataSourcePolicy.isPrimary(
                                Provider.FOOTBALL_DATA_UK, Dataset.HISTORICAL_ODDS, input.seasonYear()))
                .toList();
        if (valid.isEmpty()) return 0;

        Map<String, Bookmaker> bookmakerCache = prefetchBookmakers();
        List<Long> matchIds = valid.stream().map(input -> input.match().getId()).distinct().toList();
        Map<OddsIdentity, MatchOdds> oddsByKey = matchOddsRepository.findAllByMatchIdIn(matchIds).stream()
                .collect(Collectors.toMap(odds -> new OddsIdentity(odds.getMatch().getId(),
                                odds.getBookmaker().getId(), odds.getMarketName()),
                        odds -> odds, (left, right) -> left));
        List<MatchOdds> changedOdds = new ArrayList<>();
        List<MatchOddsQuotePersistenceService.QuoteBatchInput> quoteBatches = new ArrayList<>(valid.size());
        int saved = 0;
        for (OddsInput input : valid) {
            Match match = input.match();
            Map<Bookmaker, List<MatchOddsQuotePersistenceService.QuoteInput>> quoteBatch = new LinkedHashMap<>();
            for (BookmakerSource source : BOOKMAKERS) {
                saved += persistBookmaker(match, input.row(), source, false, quoteBatch,
                        bookmakerCache, oddsByKey, changedOdds);
                saved += persistBookmaker(match, input.row(), source, true, quoteBatch,
                        bookmakerCache, oddsByKey, changedOdds);
            }
            for (AggregateSource source : AGGREGATES) {
                saved += persistAggregate(match, input.row(), source, quoteBatch,
                        bookmakerCache, oddsByKey, changedOdds);
            }
            if (!quoteBatch.isEmpty()) {
                quoteBatches.add(new MatchOddsQuotePersistenceService.QuoteBatchInput(
                        match, MatchProvider.FOOTBALL_DATA_UK, quoteBatch));
            }
        }
        if (!changedOdds.isEmpty()) matchOddsRepository.saveAll(changedOdds);
        if (!quoteBatches.isEmpty()) quotePersistenceService.upsertAll(quoteBatches);
        return saved;
    }

    private int persistBookmaker(Match match, Map<String, String> row,
                                 BookmakerSource source, boolean closing, Map<Bookmaker, List<MatchOddsQuotePersistenceService.QuoteInput>> quoteBatch, Map<String, Bookmaker> bookmakerCache, Map<OddsIdentity, MatchOdds> oddsByKey, List<MatchOdds> changedOdds) {
        String period = closing ? "C" : "";
        BigDecimal home = firstOdds(row, source.oneXTwoPrefixes(), period, "H");
        BigDecimal draw = firstOdds(row, source.oneXTwoPrefixes(), period, "D");
        BigDecimal away = firstOdds(row, source.oneXTwoPrefixes(), period, "A");
        int saved = persistIfPresent(match, source.name(), false,
                closing ? "1X2_CLOSE" : "1X2_OPEN", null,
                home, draw, away, null, null, null, null, null, quoteBatch, bookmakerCache, oddsByKey, changedOdds);

        BigDecimal over = firstOdds(row, source.totalsPrefixes(), period, ">2.5");
        BigDecimal under = firstOdds(row, source.totalsPrefixes(), period, "<2.5");
        saved += persistIfPresent(match, source.name(), false,
                closing ? "OU25_CLOSE" : "OU25_OPEN", null,
                null, null, null, over, under, null, null, null, quoteBatch, bookmakerCache, oddsByKey, changedOdds);

        BigDecimal line = firstSigned(row, source.handicapPrefixes(), period, "AH");
        BigDecimal handicapHome = firstOdds(row, source.handicapPrefixes(), period, "AHH");
        BigDecimal handicapAway = firstOdds(row, source.handicapPrefixes(), period, "AHA");
        saved += persistIfPresent(match, source.name(), false,
                closing ? "AH_CLOSE" : "AH_OPEN", line,
                null, null, null, null, null, handicapHome, handicapAway, null, quoteBatch, bookmakerCache, oddsByKey, changedOdds);
        return saved;
    }

    private int persistAggregate(Match match, Map<String, String> row, AggregateSource source, Map<Bookmaker, List<MatchOddsQuotePersistenceService.QuoteInput>> quoteBatch, Map<String, Bookmaker> bookmakerCache, Map<OddsIdentity, MatchOdds> oddsByKey, List<MatchOdds> changedOdds) {
        String prefix = source.prefix();
        Integer oneXTwoCount = source.betBrain() ? integer(value(row, "BB1X2")) : null;
        BigDecimal home = decimalOdds(value(row, prefix + "H"));
        BigDecimal draw = decimalOdds(value(row, prefix + "D"));
        BigDecimal away = decimalOdds(value(row, prefix + "A"));
        int saved = persistIfPresent(match, source.name(), true, "1X2_OPEN", null,
                home, draw, away, null, null, null, null, oneXTwoCount, quoteBatch, bookmakerCache, oddsByKey, changedOdds);

        BigDecimal over = decimalOdds(value(row, prefix + ">2.5"));
        BigDecimal under = decimalOdds(value(row, prefix + "<2.5"));
        Integer totalsCount = source.betBrain() ? integer(value(row, "BBOU")) : null;
        saved += persistIfPresent(match, source.name(), true, "OU25_OPEN", null,
                null, null, null, over, under, null, null, totalsCount, quoteBatch, bookmakerCache, oddsByKey, changedOdds);

        BigDecimal ahHome = decimalOdds(value(row, prefix + "AHH"));
        BigDecimal ahAway = decimalOdds(value(row, prefix + "AHA"));
        BigDecimal ahLine = source.betBrain()
                ? decimalSigned(value(row, "BBAHH"))
                : decimalSigned(value(row, "AHH"));
        Integer ahCount = source.betBrain() ? integer(value(row, "BBAH")) : null;
        saved += persistIfPresent(match, source.name(), true, "AH_OPEN", ahLine,
                null, null, null, null, null, ahHome, ahAway, ahCount, quoteBatch, bookmakerCache, oddsByKey, changedOdds);
        return saved;
    }

    private int persistIfPresent(Match match, String bookmakerName, boolean aggregate,
                                 String market, BigDecimal line,
                                 BigDecimal home, BigDecimal draw, BigDecimal away,
                                 BigDecimal over, BigDecimal under,
                                 BigDecimal asianHome, BigDecimal asianAway,
                                 Integer bookmakerCount, Map<Bookmaker, List<MatchOddsQuotePersistenceService.QuoteInput>> quoteBatch,
                                 Map<String, Bookmaker> bookmakerCache, Map<OddsIdentity, MatchOdds> oddsByKey,
                                 List<MatchOdds> changedOdds) {
        boolean hasOdds = home != null || draw != null || away != null || over != null || under != null
                || asianHome != null || asianAway != null;
        if (!hasOdds) return 0;

        Bookmaker bookmaker = resolveBookmaker(bookmakerName, aggregate, bookmakerCache);

        OddsIdentity key = new OddsIdentity(match.getId(), bookmaker.getId(), market);
        MatchOdds odds = oddsByKey.computeIfAbsent(key, ignored -> MatchOdds.builder()
                .match(match).bookmaker(bookmaker).marketName(market).build());

        boolean changed = false;
        changed |= setIfMissing(odds.getAsianHandicapLine(), line, odds::setAsianHandicapLine);
        changed |= setIfMissing(odds.getHomeWinOdds(), home, odds::setHomeWinOdds);
        changed |= setIfMissing(odds.getDrawOdds(), draw, odds::setDrawOdds);
        changed |= setIfMissing(odds.getAwayWinOdds(), away, odds::setAwayWinOdds);
        changed |= setIfMissing(odds.getOver25Odds(), over, odds::setOver25Odds);
        changed |= setIfMissing(odds.getUnder25Odds(), under, odds::setUnder25Odds);
        changed |= setIfMissing(odds.getAsianHandicapHome(), asianHome, odds::setAsianHandicapHome);
        changed |= setIfMissing(odds.getAsianHandicapAway(), asianAway, odds::setAsianHandicapAway);
        changed |= setIfMissing(odds.getBookmakerCount(), bookmakerCount, odds::setBookmakerCount);
        if (changed && !changedOdds.contains(odds)) changedOdds.add(odds);

        if (market.startsWith("1X2")) {
            addQuote(quoteBatch, bookmaker, market, "HOME", null, home);
            addQuote(quoteBatch, bookmaker, market, "DRAW", null, draw);
            addQuote(quoteBatch, bookmaker, market, "AWAY", null, away);
        } else if (market.startsWith("OU25")) {
            addQuote(quoteBatch, bookmaker, market, "Over 2.5", new BigDecimal("2.5"), over);
            addQuote(quoteBatch, bookmaker, market, "Under 2.5", new BigDecimal("2.5"), under);
        } else if (market.startsWith("AH")) {
            addQuote(quoteBatch, bookmaker, market, "HOME", line, asianHome);
            addQuote(quoteBatch, bookmaker, market, "AWAY", line, asianAway);
        }
        return changed ? 1 : 0;
    }

    private void addQuote(Map<Bookmaker, List<MatchOddsQuotePersistenceService.QuoteInput>> quoteBatch,
                          Bookmaker bookmaker, String market, String outcome,
                          BigDecimal line, BigDecimal odd) {
        if (odd == null || odd.signum() <= 0) return;
        quoteBatch.computeIfAbsent(bookmaker, ignored -> new ArrayList<>())
                .add(new MatchOddsQuotePersistenceService.QuoteInput(market, outcome, line, odd));
    }

    private Map<String, Bookmaker> prefetchBookmakers() {
        List<String> names = new ArrayList<>();
        BOOKMAKERS.forEach(source -> {
            names.add(source.name());
            String legacy = legacyBookmakerName(source.name());
            if (legacy != null) names.add(legacy);
        });
        AGGREGATES.forEach(source -> {
            names.add(source.name());
            String legacy = legacyBookmakerName(source.name());
            if (legacy != null) names.add(legacy);
        });
        return bookmakerRepository.findAllByNameIn(names).stream()
                .collect(Collectors.toMap(Bookmaker::getName, bookmaker -> bookmaker, (left, right) -> left));
    }

    private record OddsIdentity(Long matchId, Long bookmakerId, String marketName) {}

    private Bookmaker resolveBookmaker(String canonicalName, boolean aggregate,
                                       Map<String, Bookmaker> bookmakerCache) {
        String sourceType = aggregate ? "MARKET_AGGREGATE" : "BOOKMAKER";
        Bookmaker bookmaker = bookmakerCache.get(canonicalName);
        boolean changed = false;
        if (bookmaker == null) {
            String legacyName = legacyBookmakerName(canonicalName);
            if (legacyName != null) {
                bookmaker = bookmakerCache.get(legacyName);
                if (bookmaker != null) {
                    bookmaker.setName(canonicalName);
                    changed = true;
                }
            }
        }
        if (bookmaker == null) {
            bookmaker = Bookmaker.builder().name(canonicalName)
                    .sourceType(sourceType).isActive(true).build();
            changed = true;
        } else if (!sourceType.equals(bookmaker.getSourceType())) {
            bookmaker.setSourceType(sourceType);
            changed = true;
        }
        if (changed) bookmaker = bookmakerRepository.save(bookmaker);
        bookmakerCache.put(canonicalName, bookmaker);
        return bookmaker;
    }

    private String legacyBookmakerName(String canonicalName) {
        return switch (canonicalName) {
            case "Bet365" -> "B365";
            case "Betfred" -> "BFD";
            case "BetVictor" -> "BV";
            case "Bet&Win" -> "BW";
            case "Paddy Power" -> "PP";
            case "Skybet" -> "SKB";
            case "Betfair Exchange" -> "BFE";
            case "Market Max" -> "MAX";
            case "Market Average" -> "AVG";
            default -> null;
        };
    }

    private boolean setIfMissing(BigDecimal current, BigDecimal incoming,
                                 Consumer<BigDecimal> setter) {
        if (current != null || incoming == null) return false;
        setter.accept(incoming);
        return true;
    }

    private boolean setIfMissing(Integer current, Integer incoming, Consumer<Integer> setter) {
        if (current != null || incoming == null) return false;
        setter.accept(incoming);
        return true;
    }

    private BigDecimal firstOdds(Map<String, String> row, List<String> prefixes,
                                 String period, String suffix) {
        for (String prefix : prefixes) {
            BigDecimal candidate = decimalOdds(value(row, prefix + period + suffix));
            if (candidate != null) return candidate;
        }
        return null;
    }

    private BigDecimal firstSigned(Map<String, String> row, List<String> prefixes,
                                   String period, String suffix) {
        for (String prefix : prefixes) {
            BigDecimal candidate = decimalSigned(value(row, prefix + period + suffix));
            if (candidate != null) return candidate;
        }
        return null;
    }

    private static BigDecimal decimalOdds(String raw) {
        BigDecimal value = decimalSigned(raw);
        return value != null && value.signum() > 0 ? value : null;
    }

    private static BigDecimal decimalSigned(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Integer integer(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        try {
            double parsed = Double.parseDouble(raw.trim().replace(",", ""));
            if (!Double.isFinite(parsed) || parsed < 0 || parsed != Math.rint(parsed)
                    || parsed > Integer.MAX_VALUE) return null;
            return (int) parsed;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String value(Map<String, String> row, String key) {
        return row.getOrDefault(key, "").trim();
    }

    private record BookmakerSource(String name, List<String> oneXTwoPrefixes,
                                   List<String> totalsPrefixes, List<String> handicapPrefixes) {
    }

    private record AggregateSource(String name, String prefix, boolean betBrain) {
    }
}
